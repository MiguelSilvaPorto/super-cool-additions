package com.sccadd.village;

import de.maxhenkel.easyvillagers.datacomponents.VillagerData;
import de.maxhenkel.easyvillagers.entity.EasyVillagerEntity;
import de.maxhenkel.easyvillagers.items.VillagerItem;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.NetworkBlockEntity;
import de.scc.calculator.network.NetworkView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

/**
 * A villager that lives in the block. It is on the SCC network, so it pays its trades from the
 * network's stock. It trades emeralds to level up, looks for the trade you asked for at every
 * level, and hands what it buys to the network or to the inventories beside it.
 *
 * <p>Draft: it is driven by using items on the block, there is no screen yet.</p>
 */
public final class VillageSimulatorBlockEntity extends NetworkBlockEntity {
    public enum Output {
        /** Back into the SCC network. */
        NETWORK,
        /** Into the inventories around the block first, the network for what does not fit. */
        NEARBY
    }

    private static final int STEP_EVERY = 20;
    private static final int TRADES_PER_STEP = 16;
    private static final int MAX_PENDING = 64;
    private static final Method LEVEL_UP = levelUpMethod();

    private ItemStack villager = ItemStack.EMPTY;
    @Nullable
    private ResourceLocation workstation;
    @Nullable
    private StackKey wanted;
    private Output output = Output.NETWORK;
    private String state = "empty";
    private long lastRestock;
    private final List<ItemStack> pending = new ArrayList<>();

    @Nullable
    private EasyVillagerEntity entity;
    /** Diagnostics only: trades cost nothing, nothing is taken from a network. */
    private boolean freeTrades;
    private int trades;

    public VillageSimulatorBlockEntity(BlockPos pos, BlockState state) {
        super(VillageRegistry.SIMULATOR_BE.get(), pos, state);
    }

    // ---- hooks used by the block ----

    public boolean useItem(Player player, InteractionHand hand, ItemStack held) {
        if (held.getItem() instanceof VillagerItem) {
            if (!this.villager.isEmpty()) {
                player.displayClientMessage(Component.literal("There is already a villager inside."), true);
                return true;
            }
            this.villager = held.copyWithCount(1);
            held.shrink(1);
            this.entity = null;
            this.state = "villager moved in";
            this.changed();
            player.displayClientMessage(Component.literal("Villager moved in."), true);
            return true;
        }
        if (held.is(Items.HOPPER)) {
            this.output = this.output == Output.NETWORK ? Output.NEARBY : Output.NETWORK;
            this.changed();
            player.displayClientMessage(Component.literal(this.output == Output.NETWORK
                    ? "Output: back into the network" : "Output: nearby inventories first, then the network"), true);
            return true;
        }
        if (held.getItem() instanceof BlockItem blockItem && professionFor(blockItem.getBlock()) != null) {
            ResourceLocation old = this.workstation;
            this.workstation = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
            if (!player.getAbilities().instabuild) {
                if (old != null) {
                    BuiltInRegistries.ITEM.getOptional(old).ifPresent(item -> ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(item)));
                }
                held.shrink(1);
            }
            this.entity = null;
            this.changed();
            player.displayClientMessage(Component.literal("Workstation: " + blockItem.getBlock().getName().getString()), true);
            return true;
        }
        // any other item is the trade you want: the villager looks for a trade that gives it
        StackKey key = StackKey.of(held);
        if (key.equals(this.wanted) && player.isShiftKeyDown()) {
            this.wanted = null;
            player.displayClientMessage(Component.literal("No trade wanted."), true);
        } else {
            this.wanted = key;
            player.displayClientMessage(Component.literal("Looking for a trade that gives " + held.getHoverName().getString()), true);
        }
        this.changed();
        return true;
    }

    public void useEmptyHand(Player player) {
        if (player.isShiftKeyDown() && !this.villager.isEmpty()) {
            this.saveEntity();
            ItemHandlerHelper.giveItemToPlayer(player, this.villager);
            this.villager = ItemStack.EMPTY;
            this.entity = null;
            this.state = "empty";
            this.changed();
            player.displayClientMessage(Component.literal("Villager taken out."), true);
            return;
        }
        for (String line : this.status()) {
            player.displayClientMessage(Component.literal(line), false);
        }
    }

    public List<String> status() {
        List<String> lines = new ArrayList<>();
        lines.add("Village Simulator: " + this.state);
        EasyVillagerEntity villager = this.entity();
        if (villager == null) {
            lines.add("Villager: none (use an Easy Villagers villager item on the block)");
        } else {
            var data = villager.getVillagerData();
            lines.add("Villager: " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.getProfession()).getPath()
                    + " level " + data.getLevel() + ", xp " + villager.getVillagerXp());
        }
        lines.add("Workstation: " + (this.workstation == null ? "none" : this.workstation.toString()));
        lines.add("Wants a trade for: " + (this.wanted == null ? "nothing (only levels up)" : this.wanted.display().getHoverName().getString()));
        lines.add("Output: " + (this.output == Output.NETWORK ? "network" : "nearby inventories, then network")
                + (this.pending.isEmpty() ? "" : ", " + this.pending.size() + " stack(s) waiting for room"));
        lines.add("Trades done: " + this.trades);
        return lines;
    }

    public void dropContents(Level level, BlockPos pos) {
        this.saveEntity();
        if (!this.villager.isEmpty()) {
            Block.popResource(level, pos, this.villager);
            this.villager = ItemStack.EMPTY;
        }
        for (ItemStack stack : this.pending) {
            Block.popResource(level, pos, stack);
        }
        this.pending.clear();
        if (this.workstation != null) {
            BuiltInRegistries.ITEM.getOptional(this.workstation).ifPresent(item -> Block.popResource(level, pos, new ItemStack(item)));
        }
    }

    // ---- the work ----

    public void serverTick() {
        if (this.level == null || this.level.getGameTime() % STEP_EVERY != 7) {
            return;
        }
        this.step();
    }

    /** One pass of the work; also what the diagnostic command calls. */
    public void step() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        String before = this.state;
        this.flushPending();
        EasyVillagerEntity villager = this.entity();
        if (villager == null) {
            this.state = "empty";
        } else if (this.workstation == null) {
            this.state = "needs a workstation";
        } else if (!this.fixProfession(villager)) {
            this.state = "the workstation gives no profession";
        } else {
            NetworkView view = this.freeTrades ? null : this.network();
            if (!this.freeTrades && (view == null || !view.online())) {
                this.state = "not on a running network";
            } else {
                this.work(villager, view);
            }
        }
        if (!before.equals(this.state)) {
            this.changed();
        }
    }

    private void work(EasyVillagerEntity villager, @Nullable NetworkView view) {
        MerchantOffers offers = villager.getOffers();
        if (offers == null || offers.isEmpty()) {
            this.state = "this villager has no trades";
            return;
        }
        for (int i = 0; i < TRADES_PER_STEP; i++) {
            MerchantOffer wantedOffer = this.wantedOffer(offers);
            if (wantedOffer != null) {
                if (wantedOffer.isOutOfStock()) {
                    if (!this.restock(villager)) {
                        this.state = "the wanted trade is sold out, waiting for a restock";
                        return;
                    }
                    continue;
                }
                if (!this.trade(villager, wantedOffer, view, true)) {
                    return;
                }
                continue;
            }
            int level = villager.getVillagerData().getLevel();
            if (level >= 5 && this.wanted != null) {
                this.state = "the wanted trade is not offered at the top level";
                return;
            }
            if (level >= 5 || this.wanted == null && !this.shouldLevel(villager)) {
                this.state = this.wanted == null ? "idle (set a wanted trade)" : "the wanted trade is not offered at the top level";
                return;
            }
            MerchantOffer levelling = this.levellingOffer(offers, view);
            if (levelling == null) {
                if (!this.anyAvailable(offers) && this.restock(villager)) {
                    continue;
                }
                this.state = "levelling up needs emeralds the network does not have";
                return;
            }
            if (!this.trade(villager, levelling, view, false)) {
                return;
            }
            this.levelUpIfReady(villager);
            offers = villager.getOffers();
        }
        this.saveEntity();
    }

    private boolean shouldLevel(EasyVillagerEntity villager) {
        return villager.getVillagerData().getLevel() < 5;
    }

    @Nullable
    private MerchantOffer wantedOffer(MerchantOffers offers) {
        if (this.wanted == null) {
            return null;
        }
        for (MerchantOffer offer : offers) {
            if (StackKey.of(offer.getResult()).equals(this.wanted)) {
                return offer;
            }
        }
        return null;
    }

    /** The cheapest trade the network can pay, emerald-only trades first. */
    @Nullable
    private MerchantOffer levellingOffer(MerchantOffers offers, @Nullable NetworkView view) {
        MerchantOffer best = null;
        int bestScore = Integer.MAX_VALUE;
        for (MerchantOffer offer : offers) {
            if (offer.isOutOfStock() || !this.payable(offer, view)) {
                continue;
            }
            boolean emeraldOnly = offer.getCostA().is(Items.EMERALD) && offer.getCostB().isEmpty();
            int score = (emeraldOnly ? 0 : 1000) + offer.getCostA().getCount() + offer.getCostB().getCount();
            if (score < bestScore) {
                best = offer;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean anyAvailable(MerchantOffers offers) {
        for (MerchantOffer offer : offers) {
            if (!offer.isOutOfStock()) {
                return true;
            }
        }
        return false;
    }

    private boolean payable(MerchantOffer offer, @Nullable NetworkView view) {
        return this.freeTrades || this.has(offer.getCostA(), view) && (offer.getCostB().isEmpty() || this.has(offer.getCostB(), view));
    }

    private boolean has(ItemStack cost, @Nullable NetworkView view) {
        return cost.isEmpty() || view != null && view.count(StackKey.of(cost)) >= cost.getCount();
    }

    private boolean trade(EasyVillagerEntity villager, MerchantOffer offer, @Nullable NetworkView view, boolean wantedTrade) {
        ItemStack a = offer.getCostA();
        ItemStack b = offer.getCostB();
        if (!this.payable(offer, view)) {
            this.state = "missing " + (this.has(a, view) ? b : a).getHoverName().getString() + " in the network";
            return false;
        }
        if (!this.freeTrades && view != null) {
            if (!take(view, a) || !take(view, b)) {
                this.state = "could not take the payment from the network";
                return false;
            }
        }
        offer.increaseUses();
        villager.setVillagerXp(villager.getVillagerXp() + offer.getXp());
        this.trades++;
        ItemStack result = offer.getResult().copy();
        this.deliver(result, view);
        this.state = wantedTrade ? "trading for the wanted item" : "levelling up";
        return true;
    }

    private static boolean take(NetworkView view, ItemStack cost) {
        if (cost.isEmpty()) {
            return true;
        }
        ItemStack got = view.extract(StackKey.of(cost), cost.getCount(), false);
        if (got.getCount() < cost.getCount()) {
            if (!got.isEmpty()) {
                view.insert(got, false);
            }
            return false;
        }
        return true;
    }

    private void levelUpIfReady(EasyVillagerEntity villager) {
        int level = villager.getVillagerData().getLevel();
        if (net.minecraft.world.entity.npc.VillagerData.canLevelUp(level)
                && villager.getVillagerXp() >= net.minecraft.world.entity.npc.VillagerData.getMaxXpPerLevel(level)) {
            try {
                if (LEVEL_UP != null) {
                    LEVEL_UP.invoke(villager);
                }
            } catch (ReflectiveOperationException failed) {
                this.state = "could not level the villager up";
            }
        }
    }

    private boolean restock(EasyVillagerEntity villager) {
        long now = this.level.getGameTime();
        if (now - this.lastRestock < 1200L) {
            return false;
        }
        this.lastRestock = now;
        villager.restock();
        return true;
    }

    private boolean fixProfession(EasyVillagerEntity villager) {
        Block block = this.workstation == null ? null : BuiltInRegistries.BLOCK.get(this.workstation);
        VillagerProfession wanted = block == null ? null : professionFor(block);
        if (wanted == null) {
            return false;
        }
        if (villager.getVillagerXp() == 0 && !villager.getVillagerData().getProfession().equals(wanted)) {
            villager.setVillagerData(villager.getVillagerData().setProfession(wanted));
            villager.recalculateOffers();
        }
        return true;
    }

    @Nullable
    public static VillagerProfession professionFor(Block block) {
        Optional<Holder<PoiType>> poi = PoiTypes.forState(block.defaultBlockState());
        if (poi.isEmpty()) {
            return null;
        }
        for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            // "none" accepts every job site and the nitwit none at all: neither is a real job
            if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) {
                continue;
            }
            if (profession.heldJobSite().test(poi.get())) {
                return profession;
            }
        }
        return null;
    }

    // ---- output ----

    private void deliver(ItemStack result, @Nullable NetworkView view) {
        ItemStack left = result;
        if (this.output == Output.NEARBY && this.level != null) {
            for (Direction direction : Direction.values()) {
                if (left.isEmpty()) {
                    break;
                }
                IItemHandler handler = this.level.getCapability(Capabilities.ItemHandler.BLOCK,
                        this.worldPosition.relative(direction), direction.getOpposite());
                if (handler != null) {
                    left = ItemHandlerHelper.insertItem(handler, left, false);
                }
            }
        }
        if (!left.isEmpty() && view != null && view.online()) {
            left = view.insert(left, false);
        }
        if (!left.isEmpty() && (this.freeTrades || this.pending.size() < MAX_PENDING)) {
            this.pending.add(left);
        }
    }

    private void flushPending() {
        if (this.pending.isEmpty()) {
            return;
        }
        NetworkView view = this.freeTrades ? null : this.network();
        List<ItemStack> again = new ArrayList<>(this.pending);
        this.pending.clear();
        for (ItemStack stack : again) {
            this.deliver(stack, view);
        }
    }

    // ---- the villager ----

    @Nullable
    private EasyVillagerEntity entity() {
        if (this.villager.isEmpty() || this.level == null) {
            return null;
        }
        if (this.entity == null) {
            this.entity = VillagerData.createEasyVillager(this.villager, this.level);
        }
        return this.entity;
    }

    private void saveEntity() {
        if (this.entity != null && !this.villager.isEmpty()) {
            VillagerData.applyToItem(this.villager, this.entity);
        }
    }

    private void changed() {
        this.saveEntity();
        this.setChanged();
    }

    // ---- diagnostics ----

    /** Used by the diagnostic command: a villager with a workstation and free trades. */
    public void selfTest(ItemStack villagerItem, Block workstation, StackKey wanted) {
        this.freeTrades = true;
        this.villager = villagerItem;
        this.entity = null;
        this.workstation = BuiltInRegistries.BLOCK.getKey(workstation);
        this.wanted = wanted;
        this.output = Output.NETWORK;
        this.pending.clear();
        this.trades = 0;
    }

    public int trades() {
        return this.trades;
    }

    public List<ItemStack> waiting() {
        return this.pending;
    }

    public String stateText() {
        return this.state;
    }

    public static ItemStack newVillagerItem(Level level, VillagerProfession profession) {
        EasyVillagerEntity made = new EasyVillagerEntity(EntityType.VILLAGER, level);
        made.setVillagerData(made.getVillagerData().setProfession(profession));
        ItemStack stack = new ItemStack(de.maxhenkel.easyvillagers.items.ModItems.VILLAGER.get());
        VillagerData.applyToItem(stack, made);
        return stack;
    }

    // ---- save / load ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        this.saveEntity();
        if (!this.villager.isEmpty()) {
            tag.put("sccadd_villager", this.villager.save(provider));
        }
        if (this.workstation != null) {
            tag.putString("sccadd_workstation", this.workstation.toString());
        }
        if (this.wanted != null) {
            tag.putString("sccadd_wanted", this.wanted.encode());
        }
        tag.putString("sccadd_output", this.output.name());
        tag.putString("sccadd_state", this.state);
        tag.putLong("sccadd_restock", this.lastRestock);
        ListTag list = new ListTag();
        for (ItemStack stack : this.pending) {
            list.add(stack.save(provider));
        }
        tag.put("sccadd_pending", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.villager = tag.contains("sccadd_villager") ? ItemStack.parseOptional(provider, tag.getCompound("sccadd_villager")) : ItemStack.EMPTY;
        this.entity = null;
        this.workstation = tag.contains("sccadd_workstation") ? ResourceLocation.tryParse(tag.getString("sccadd_workstation")) : null;
        this.wanted = tag.contains("sccadd_wanted") ? StackKey.decode(tag.getString("sccadd_wanted")) : null;
        try {
            this.output = Output.valueOf(tag.getString("sccadd_output"));
        } catch (IllegalArgumentException unknown) {
            this.output = Output.NETWORK;
        }
        this.state = tag.contains("sccadd_state") ? tag.getString("sccadd_state") : "empty";
        this.lastRestock = tag.getLong("sccadd_restock");
        this.pending.clear();
        ListTag list = tag.getList("sccadd_pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(provider, list.getCompound(i));
            if (!stack.isEmpty()) {
                this.pending.add(stack);
            }
        }
    }

    @Nullable
    private static Method levelUpMethod() {
        try {
            Method method = Villager.class.getDeclaredMethod("increaseMerchantCareer");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException missing) {
            return null;
        }
    }
}
