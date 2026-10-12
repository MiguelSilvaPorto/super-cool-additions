package com.sccadd.village;

import de.maxhenkel.easyvillagers.datacomponents.VillagerData;
import de.maxhenkel.easyvillagers.entity.EasyVillagerEntity;
import de.maxhenkel.easyvillagers.items.VillagerItem;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.NetworkBlockEntity;
import de.scc.calculator.network.NetworkView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
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
import net.minecraft.world.item.trading.Merchant;
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
 * network's stock.
 *
 * <ul>
 *   <li><b>Trader</b>: you open its trade screen; the payment is taken from the network.</li>
 *   <li><b>Auto Trader</b>: it trades the offers you picked, on its own, and hands what it buys to
 *       the network or to the inventories beside it. It also trades emeralds to level up until the
 *       offers you want are unlocked.</li>
 * </ul>
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
    private static final Method START_TRADING = villagerMethod("startTrading", Player.class);
    private static final Map<Merchant, VillageSimulatorBlockEntity> OWNERS = new WeakHashMap<>();

    private ItemStack villager = ItemStack.EMPTY;
    @Nullable
    private ResourceLocation workstation;
    /** Auto Trader: the offers to trade, by {@link #signature}, with how many of the result to keep (0 = no limit). */
    private final Map<String, Integer> picked = new LinkedHashMap<>();
    /** Auto Trader: level the villager up with emerald trades until the picked offers are there. */
    private boolean levelUp = true;
    private Output output = Output.NETWORK;
    private String state = "empty";
    private long lastRestock;
    private final List<ItemStack> pending = new ArrayList<>();
    /** Version of the villager stack, so the client rebuilds its entity only when it changed. */
    private int villagerVersion;

    @Nullable
    private EasyVillagerEntity entity;
    @Nullable
    private EasyVillagerEntity clientEntity;
    private int clientVersion = -1;
    /** Diagnostics only: trades cost nothing, nothing is taken from a network. */
    private boolean freeTrades;
    @Nullable
    private StackKey testWanted;
    private int trades;

    public VillageSimulatorBlockEntity(BlockPos pos, BlockState state) {
        super(VillageRegistry.VILLAGE_BE.get(), pos, state);
    }

    public boolean isAuto() {
        return this.getBlockState().getBlock() instanceof VillageBlockBase base && base.isAuto();
    }

    @Nullable
    public static VillageSimulatorBlockEntity ownerOf(Merchant merchant) {
        synchronized (OWNERS) {
            return OWNERS.get(merchant);
        }
    }

    /** What identifies an offer across restocks and price changes: what it gives and what it takes. */
    public static String signature(MerchantOffer offer) {
        String b = offer.getCostB().isEmpty() ? "" : StackKey.of(offer.getCostB()).encode();
        return StackKey.of(offer.getResult()).encode() + "|" + StackKey.of(offer.getCostA()).encode() + "|" + b;
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
        return false;
    }

    public void useEmptyHand(Player player) {
        if (player.isShiftKeyDown()) {
            if (!this.villager.isEmpty()) {
                this.saveEntity();
                ItemHandlerHelper.giveItemToPlayer(player, this.villager);
                this.villager = ItemStack.EMPTY;
                this.entity = null;
                this.state = "empty";
                this.changed();
                player.displayClientMessage(Component.literal("Villager taken out."), true);
            } else {
                for (String line : this.status()) {
                    player.displayClientMessage(Component.literal(line), false);
                }
            }
            return;
        }
        if (this.isAuto()) {
            VillageMenus.openConfig(player, this);
        } else {
            this.openTrade(player);
        }
    }

    /** The trade screen of the villager; what it costs comes out of the network, not your pockets. */
    public void openTrade(Player player) {
        EasyVillagerEntity villager = this.entity();
        if (villager == null) {
            player.displayClientMessage(Component.literal("No villager inside. Use an Easy Villagers villager item on the block."), true);
        } else if (this.workstation == null || !this.fixProfession(villager)) {
            player.displayClientMessage(Component.literal("The villager needs a workstation: use one on the block."), true);
        } else if (villager.getOffers().isEmpty()) {
            player.displayClientMessage(Component.literal("This villager has nothing to trade."), true);
        } else if (villager.isTrading()) {
            player.displayClientMessage(Component.literal("Someone is already trading."), true);
        } else {
            villager.setPos(this.worldPosition.getX() + 0.5, this.worldPosition.getY(), this.worldPosition.getZ() + 0.5);
            this.startTrading(villager, player);
        }
    }

    private void startTrading(EasyVillagerEntity villager, Player player) {
        try {
            if (START_TRADING != null) {
                START_TRADING.invoke(villager, player);
                return;
            }
        } catch (ReflectiveOperationException failed) {
            // fall through to the plain way
        }
        villager.setTradingPlayer(player);
        villager.openTradingScreen(player, villager.getDisplayName(), villager.getVillagerData().getLevel());
    }

    public List<String> status() {
        List<String> lines = new ArrayList<>();
        lines.add((this.isAuto() ? "Village Auto Trader: " : "Village Trader: ") + this.state);
        EasyVillagerEntity villager = this.entity();
        if (villager == null) {
            lines.add("Villager: none (use an Easy Villagers villager item on the block)");
        } else {
            var data = villager.getVillagerData();
            lines.add("Villager: " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.getProfession()).getPath()
                    + " level " + data.getLevel() + ", xp " + villager.getVillagerXp());
        }
        lines.add("Workstation: " + (this.workstation == null ? "none" : this.workstation.toString()));
        if (this.isAuto()) {
            lines.add("Trading " + this.picked.size() + " offer(s); output: "
                    + (this.output == Output.NETWORK ? "network" : "nearby inventories, then network")
                    + (this.pending.isEmpty() ? "" : ", " + this.pending.size() + " stack(s) waiting for room"));
        }
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

    // ---- the configuration screen's view ----

    public Map<String, Integer> picked() {
        return this.picked;
    }

    public boolean levelUp() {
        return this.levelUp;
    }

    public Output output() {
        return this.output;
    }

    public void setLevelUp(boolean value) {
        this.levelUp = value;
        this.changed();
    }

    public void setOutput(Output value) {
        this.output = value;
        this.changed();
    }

    /** Picks or drops an offer; {@code keep} is how many of the result the network should hold (0 = no limit). */
    public void pick(String signature, boolean on, int keep) {
        if (on) {
            this.picked.put(signature, Math.max(0, keep));
        } else {
            this.picked.remove(signature);
        }
        this.changed();
    }

    public MerchantOffers offers() {
        EasyVillagerEntity villager = this.entity();
        return villager == null ? new MerchantOffers() : villager.getOffers();
    }

    public String workstationName() {
        Block block = this.workstation == null ? null : BuiltInRegistries.BLOCK.get(this.workstation);
        return block == null ? "none" : block.getName().getString();
    }

    public String stateText() {
        return this.state;
    }

    public ItemStack villagerStack() {
        return this.villager;
    }

    // ---- the work ----

    public void serverTick() {
        if (this.level == null || this.level.getGameTime() % STEP_EVERY != 7) {
            return;
        }
        if (this.isAuto()) {
            this.step();
        } else {
            this.housekeeping();
        }
    }

    /** The trader has no work of its own: it only keeps the villager's progress from trades you made. */
    private void housekeeping() {
        EasyVillagerEntity villager = this.entity;
        if (villager != null && !villager.isTrading()) {
            this.levelUpIfReady(villager);
            this.saveEntity();
            if (!villager.getOffers().isEmpty() && villager.getOffers().stream().allMatch(MerchantOffer::isOutOfStock)) {
                this.restock(villager);
            }
        }
    }

    /** One pass of the Auto Trader's work; also what the diagnostic command calls. */
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
        } else if (villager.isTrading()) {
            this.state = "someone is trading";
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
        if (this.testWanted != null) {
            for (MerchantOffer offer : offers) {
                if (StackKey.of(offer.getResult()).equals(this.testWanted)) {
                    this.picked.putIfAbsent(signature(offer), 0);
                }
            }
        }
        boolean any = false;
        for (int i = 0; i < TRADES_PER_STEP; i++) {
            offers = villager.getOffers();
            boolean progressed = false;
            boolean unlockedAll = true;
            String why = null;
            for (Map.Entry<String, Integer> pick : new ArrayList<>(this.picked.entrySet())) {
                MerchantOffer offer = find(offers, pick.getKey());
                if (offer == null) {
                    unlockedAll = false;
                    continue;
                }
                int keep = pick.getValue();
                if (keep > 0 && view != null && view.count(StackKey.of(offer.getResult())) >= keep) {
                    why = "stocked";
                    continue;
                }
                if (offer.isOutOfStock()) {
                    if (this.restock(villager)) {
                        progressed = true;
                    } else {
                        why = "sold out, waiting for a restock";
                    }
                    continue;
                }
                if (!this.payable(offer, view)) {
                    why = "missing " + (this.has(offer.getCostA(), view) ? offer.getCostB() : offer.getCostA()).getHoverName().getString();
                    continue;
                }
                if (this.trade(villager, offer, view)) {
                    progressed = true;
                    any = true;
                }
            }
            if (progressed) {
                this.levelUpIfReady(villager);
                continue;
            }
            int level = villager.getVillagerData().getLevel();
            boolean needsLevels = !unlockedAll || this.picked.isEmpty();
            if (this.levelUp && needsLevels && level < 5) {
                MerchantOffer levelling = this.levellingOffer(offers, view);
                if (levelling != null && this.trade(villager, levelling, view)) {
                    this.levelUpIfReady(villager);
                    this.state = "levelling up";
                    any = true;
                    continue;
                }
                if (levelling == null && !this.anyAvailable(offers) && this.restock(villager)) {
                    continue;
                }
                this.state = "levelling up needs emeralds the network does not have";
            } else if (!unlockedAll) {
                this.state = "a picked offer is not offered at this level";
            } else {
                this.state = why != null ? why : (this.picked.isEmpty() ? "idle (pick offers in the screen)" : "working");
            }
            break;
        }
        if (any && !this.state.equals("levelling up")) {
            this.state = "trading";
        }
        this.saveEntity();
    }

    @Nullable
    private static MerchantOffer find(MerchantOffers offers, String signature) {
        for (MerchantOffer offer : offers) {
            if (signature(offer).equals(signature)) {
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
        return this.freeTrades || cost.isEmpty() || view != null && view.count(StackKey.of(cost)) >= cost.getCount();
    }

    private boolean trade(EasyVillagerEntity villager, MerchantOffer offer, @Nullable NetworkView view) {
        ItemStack a = offer.getCostA();
        ItemStack b = offer.getCostB();
        if (!this.payable(offer, view)) {
            return false;
        }
        if (!this.freeTrades && view != null) {
            if (!take(view, a)) {
                return false;
            }
            if (!take(view, b)) {
                view.insert(a.copy(), false);
                return false;
            }
        }
        offer.increaseUses();
        villager.setVillagerXp(villager.getVillagerXp() + offer.getXp());
        this.trades++;
        this.deliver(offer.getResult().copy(), view);
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
        if (this.level == null) {
            return false;
        }
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
            synchronized (OWNERS) {
                OWNERS.put(this.entity, this);
            }
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
        this.villagerVersion++;
        this.setChanged();
        this.sync();
    }

    /** Client side: the villager to draw in the box. */
    @Nullable
    public EasyVillagerEntity renderEntity() {
        if (this.level == null || this.villager.isEmpty()) {
            this.clientEntity = null;
            return null;
        }
        if (this.clientEntity == null || this.clientVersion != this.villagerVersion) {
            this.clientEntity = VillagerData.createEasyVillager(this.villager, this.level);
            this.clientVersion = this.villagerVersion;
        }
        return this.clientEntity;
    }

    // ---- diagnostics ----

    /** Used by the diagnostic command: a villager with a workstation, free trades and one wanted result. */
    public void selfTest(ItemStack villagerItem, Block workstation, StackKey wanted) {
        this.freeTrades = true;
        this.villager = villagerItem;
        this.entity = null;
        this.workstation = BuiltInRegistries.BLOCK.getKey(workstation);
        this.testWanted = wanted;
        this.picked.clear();
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
        ListTag picks = new ListTag();
        for (Map.Entry<String, Integer> pick : this.picked.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("sig", pick.getKey());
            entry.putInt("keep", pick.getValue());
            picks.add(entry);
        }
        tag.put("sccadd_picked", picks);
        tag.putBoolean("sccadd_levelup", this.levelUp);
        tag.putString("sccadd_output", this.output.name());
        tag.putString("sccadd_state", this.state);
        tag.putLong("sccadd_restock", this.lastRestock);
        tag.putInt("sccadd_version", this.villagerVersion);
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
        this.picked.clear();
        ListTag picks = tag.getList("sccadd_picked", Tag.TAG_COMPOUND);
        for (int i = 0; i < picks.size(); i++) {
            CompoundTag entry = picks.getCompound(i);
            this.picked.put(entry.getString("sig"), entry.getInt("keep"));
        }
        this.levelUp = !tag.contains("sccadd_levelup") || tag.getBoolean("sccadd_levelup");
        try {
            this.output = Output.valueOf(tag.getString("sccadd_output"));
        } catch (IllegalArgumentException unknown) {
            this.output = Output.NETWORK;
        }
        this.state = tag.contains("sccadd_state") ? tag.getString("sccadd_state") : "empty";
        this.lastRestock = tag.getLong("sccadd_restock");
        this.villagerVersion = tag.getInt("sccadd_version");
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
        return villagerMethod("increaseMerchantCareer");
    }

    @Nullable
    private static Method villagerMethod(String name, Class<?>... parameters) {
        try {
            Method method = Villager.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException missing) {
            return null;
        }
    }
}
