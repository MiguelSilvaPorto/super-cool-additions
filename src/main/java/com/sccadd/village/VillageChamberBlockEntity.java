package com.sccadd.village;

import de.maxhenkel.easyvillagers.blocks.ModBlocks;
import de.maxhenkel.easyvillagers.blocks.tileentity.AutoTraderTileentity;
import de.maxhenkel.easyvillagers.blocks.tileentity.TraderTileentity;
import de.maxhenkel.easyvillagers.blocks.tileentity.TraderTileentityBase;
import de.maxhenkel.easyvillagers.datacomponents.VillagerBlockEntityData;
import de.maxhenkel.easyvillagers.datacomponents.VillagerData;
import de.maxhenkel.easyvillagers.entity.EasyVillagerEntity;
import de.maxhenkel.easyvillagers.items.ModItems;
import de.maxhenkel.easyvillagers.items.VillagerItem;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.NetworkBlockEntity;
import de.scc.calculator.network.NetworkView;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
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
 * The chamber: a hollow block on the SCC network that holds one module of Easy Villagers - its Trader
 * or its Auto Trader - and works as it. The module is Easy Villagers' own block entity, kept inside this
 * one (in a "fake world", the way Easy Villagers keeps them in items): its villager, workstation,
 * levelling, restocking and automatic trading are all Easy Villagers' code. This block adds the
 * network: it pays trades from the system and hands the results to it.
 */
public final class VillageChamberBlockEntity extends NetworkBlockEntity {
    public enum Module {
        NONE, TRADER, AUTO
    }

    public enum Output {
        /** Back into the SCC network. */
        NETWORK,
        /** Into the inventories around the block first, the network for what does not fit. */
        NEARBY,
        /** Straight to the player who traded (manual trades only). */
        PLAYER
    }

    private static final int MAX_PENDING = 64;
    private static final Field LEVEL_UP_FLAG = villagerField("increaseProfessionLevelOnUpdate");

    private Module module = Module.NONE;
    @Nullable
    private TraderTileentityBase inner;
    /** The module's saved data, until the level is there to build it from. */
    @Nullable
    private CompoundTag innerData;
    private Output output = Output.NETWORK;
    private String state = "no module";
    private final List<ItemStack> pending = new ArrayList<>();
    /** Diagnostics only: trades cost nothing and nothing is taken from a network. */
    private boolean freeTrades;
    private int trades;

    public VillageChamberBlockEntity(BlockPos pos, BlockState state) {
        super(VillageRegistry.CHAMBER_BE.get(), pos, state);
    }

    // ---- the module ----

    public Module module() {
        return this.module;
    }

    /** The Easy Villagers block entity inside, built on first use. */
    @Nullable
    public TraderTileentityBase inner() {
        if (this.module == Module.NONE || this.level == null) {
            return null;
        }
        if (this.inner == null) {
            BlockPos pos = this.getBlockPos();
            this.inner = this.module == Module.AUTO
                    ? new AutoTraderTileentity(pos, ModBlocks.AUTO_TRADER.get().defaultBlockState())
                    : new TraderTileentity(pos, ModBlocks.TRADER.get().defaultBlockState());
            this.inner.setFakeWorld(this.level);
            if (this.innerData != null) {
                this.inner.loadCustomOnly(this.innerData, this.level.registryAccess());
            }
        }
        return this.inner;
    }

    @Nullable
    public EasyVillagerEntity villager() {
        TraderTileentityBase module = this.inner();
        return module == null || !module.hasVillager() ? null : module.getVillagerEntity();
    }

    private static Module moduleOf(ItemStack stack) {
        if (stack.is(ModItems.TRADER.get())) {
            return Module.TRADER;
        }
        if (stack.is(ModItems.AUTO_TRADER.get())) {
            return Module.AUTO;
        }
        return Module.NONE;
    }

    /** Puts an Easy Villagers Trader / Auto Trader in; what the item carried (villager, workstation) comes with it. */
    private boolean install(Player player, ItemStack held, Module wanted) {
        if (this.level == null) {
            return false;
        }
        CompoundTag data = null;
        VillagerBlockEntityData own = VillagerBlockEntityData.get(held);
        if (own != null) {
            data = own.copy();
        } else if (held.has(DataComponents.BLOCK_ENTITY_DATA)) {
            data = held.get(DataComponents.BLOCK_ENTITY_DATA).copyTag();
        }
        this.module = wanted;
        this.inner = null;
        this.innerData = data;
        this.inner();
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        this.state = "installed";
        this.changed();
        this.ensureOffers();
        player.displayClientMessage(Component.literal(wanted == Module.AUTO ? "Auto Trader installed." : "Trader installed."), true);
        return true;
    }

    /** The module as an item again, with its villager and workstation. */
    public ItemStack moduleStack() {
        TraderTileentityBase module = this.inner();
        if (module == null || this.level == null) {
            return ItemStack.EMPTY;
        }
        module.saveVillagerEntity();
        ItemStack stack = new ItemStack(this.module == Module.AUTO ? ModItems.AUTO_TRADER.get() : ModItems.TRADER.get());
        HolderLookup.Provider provider = this.level.registryAccess();
        BlockItem.setBlockEntityData(stack, module.getType(), module.saveCustomAndMetadata(provider));
        stack.applyComponents(module.collectComponents());
        return stack;
    }

    public void removeModule(Player player) {
        ItemStack stack = this.moduleStack();
        if (stack.isEmpty()) {
            return;
        }
        this.module = Module.NONE;
        this.inner = null;
        this.innerData = null;
        this.state = "no module";
        this.changed();
        ItemHandlerHelper.giveItemToPlayer(player, stack);
    }

    // ---- the block's hooks ----

    public boolean useItem(Player player, ItemStack held) {
        Module offered = moduleOf(held);
        if (offered != Module.NONE) {
            if (this.module != Module.NONE) {
                player.displayClientMessage(Component.literal("There is already a module inside: take it out in the window first."), true);
                return true;
            }
            return this.install(player, held, offered);
        }
        TraderTileentityBase module = this.inner();
        if (module == null) {
            player.displayClientMessage(Component.literal("Put an Easy Villagers Trader or Auto Trader in first."), true);
            return true;
        }
        if (!module.hasVillager() && held.getItem() instanceof VillagerItem) {
            module.setVillager(held.copyWithCount(1));
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            this.level.playSound(null, this.worldPosition, SoundEvents.VILLAGER_CELEBRATE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            this.changed();
            this.ensureOffers();
            return true;
        }
        if (!module.hasWorkstation() && held.getItem() instanceof BlockItem blockItem && module.isValidBlock(blockItem.getBlock())) {
            module.setWorkstation(blockItem.getBlock());
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            this.changed();
            this.ensureOffers();
            return true;
        }
        return false;
    }

    public void useEmptyHand(Player player) {
        if (this.module == Module.NONE) {
            player.displayClientMessage(Component.literal("Put an Easy Villagers Trader or Auto Trader in first (right-click with it)."), true);
            return;
        }
        this.ensureOffers();
        VillageMenus.openWindow(player, this);
    }

    public void dropContents(Level level, BlockPos pos) {
        ItemStack stack = this.moduleStack();
        if (!stack.isEmpty()) {
            Block.popResource(level, pos, stack);
        }
        for (ItemStack waiting : this.pending) {
            Block.popResource(level, pos, waiting);
        }
        this.pending.clear();
        this.module = Module.NONE;
        this.inner = null;
    }

    // ---- what the window shows and does ----

    public Output output() {
        return this.output;
    }

    public void setOutput(Output value) {
        this.output = value;
        this.changed();
    }

    public String stateText() {
        return this.state;
    }

    public int trades() {
        return this.trades;
    }

    /**
     * The villager's offers. They are made on the server the first time they are asked for; a client
     * copy only has them once the server sent them, and asking before that throws, so it gets none yet.
     */
    public MerchantOffers offers() {
        EasyVillagerEntity villager = this.villager();
        if (villager == null) {
            return new MerchantOffers();
        }
        try {
            MerchantOffers offers = villager.getOffers();
            return offers == null ? new MerchantOffers() : offers;
        } catch (IllegalStateException notOnTheClientYet) {
            return new MerchantOffers();
        }
    }

    /** Server: has the villager make its offers and sends them to the clients, so the window has something to show. */
    private void ensureOffers() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        TraderTileentityBase module = this.inner();
        if (module != null && module.hasVillager() && module.hasWorkstation()) {
            EasyVillagerEntity villager = module.getVillagerEntity();
            int before = villager.getVillagerXp();
            villager.getOffers();
            if (before >= 0) {
                module.saveVillagerEntity();
                this.setChanged();
                this.sync();
            }
        }
    }

    public String workstationName() {
        TraderTileentityBase module = this.inner();
        if (module == null || !module.hasWorkstation()) {
            return "none";
        }
        return module.getWorkstation().getName().getString();
    }

    public void takeVillagerOut(Player player) {
        TraderTileentityBase module = this.inner();
        if (module == null || !module.hasVillager()) {
            return;
        }
        ItemStack stack = module.removeVillager();
        this.changed();
        ItemHandlerHelper.giveItemToPlayer(player, stack);
    }

    public void takeWorkstationOut(Player player) {
        TraderTileentityBase module = this.inner();
        if (module == null || !module.hasWorkstation()) {
            return;
        }
        Block block = module.removeWorkstation();
        this.changed();
        if (block != null) {
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(block));
        }
    }

    /** A manual trade through the window: the cost comes out of the network, the result goes where the output says. */
    public void trade(int index, int times, @Nullable Player player) {
        EasyVillagerEntity villager = this.villager();
        if (villager == null || this.level == null || this.level.isClientSide) {
            return;
        }
        MerchantOffers offers = villager.getOffers();
        if (index < 0 || index >= offers.size()) {
            return;
        }
        NetworkView view = this.freeTrades ? null : this.network();
        if (!this.freeTrades && (view == null || !view.online())) {
            this.say(player, "Not on a running network.");
            return;
        }
        MerchantOffer offer = offers.get(index);
        int done = 0;
        for (int i = 0; i < Math.max(1, times); i++) {
            if (offer.isOutOfStock()) {
                this.say(player, "Sold out - it restocks over time.");
                break;
            }
            ItemStack a = offer.getCostA();
            ItemStack b = offer.getCostB();
            if (!this.freeTrades) {
                if (!has(view, a) || !has(view, b)) {
                    this.say(player, "The network does not have " + (has(view, a) ? b : a).getHoverName().getString() + ".");
                    break;
                }
                if (!take(view, a)) {
                    break;
                }
                if (!take(view, b)) {
                    view.insert(a.copy(), false);
                    break;
                }
            }
            offer.increaseUses();
            villager.setVillagerXp(villager.getVillagerXp() + offer.getXp());
            if (shouldLevelUp(villager)) {
                flagLevelUp(villager);
            }
            this.trades++;
            this.deliver(offer.getResult().copy(), view, player);
            done++;
        }
        if (done > 0) {
            this.state = "traded " + done + "x";
            TraderTileentityBase module = this.inner();
            if (module != null) {
                module.saveVillagerEntity();
            }
            this.changed();
        }
    }

    private void say(@Nullable Player player, String text) {
        this.state = text;
        if (player != null) {
            player.displayClientMessage(Component.literal(text), true);
        }
    }

    // ---- the network ----

    private static boolean has(@Nullable NetworkView view, ItemStack cost) {
        return cost.isEmpty() || view != null && view.count(StackKey.of(cost)) >= cost.getCount();
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

    private void deliver(ItemStack result, @Nullable NetworkView view, @Nullable Player player) {
        ItemStack left = result;
        if (this.output == Output.PLAYER && player != null) {
            ItemHandlerHelper.giveItemToPlayer(player, left);
            return;
        }
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
            this.deliver(stack, view, null);
        }
    }

    // ---- ticking ----

    public void serverTick() {
        TraderTileentityBase module = this.inner();
        if (module == null || this.level == null) {
            return;
        }
        module.tickServer();
        if (module instanceof AutoTraderTileentity auto) {
            auto.tick();
        }
        if (this.level.getGameTime() % 20 == 7) {
            this.flushPending();
        }
    }

    // ---- bookkeeping ----

    private void changed() {
        TraderTileentityBase module = this.inner;
        if (module != null) {
            module.saveVillagerEntity();
        }
        this.setChanged();
        this.sync();
    }

    @Nullable
    private static Field villagerField(String name) {
        try {
            Field field = Villager.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException missing) {
            return null;
        }
    }

    private static boolean shouldLevelUp(EasyVillagerEntity villager) {
        int level = villager.getVillagerData().getLevel();
        return net.minecraft.world.entity.npc.VillagerData.canLevelUp(level)
                && villager.getVillagerXp() >= net.minecraft.world.entity.npc.VillagerData.getMaxXpPerLevel(level);
    }

    /** The profession a workstation block gives, or null if it is not one. */
    @Nullable
    public static VillagerProfession professionFor(Block block) {
        java.util.Optional<net.minecraft.core.Holder<net.minecraft.world.entity.ai.village.poi.PoiType>> poi =
                net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(block.defaultBlockState());
        if (poi.isEmpty()) {
            return null;
        }
        for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            if (profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT
                    && profession.heldJobSite().test(poi.get())) {
                return profession;
            }
        }
        return null;
    }

    /** Easy Villagers' module levels the villager up on its next tick once this is set, as in vanilla. */
    private static void flagLevelUp(EasyVillagerEntity villager) {
        try {
            if (LEVEL_UP_FLAG != null) {
                LEVEL_UP_FLAG.setBoolean(villager, true);
            }
        } catch (ReflectiveOperationException ignored) {
            // it will level up when the villager ticks
        }
    }

    // ---- diagnostics ----

    /** Used by the diagnostic command: installs a module with a villager of the profession, free trades. */
    public void selfTest(Module wanted, ItemStack villagerItem, Block workstation) {
        this.freeTrades = true;
        this.module = wanted;
        this.inner = null;
        this.innerData = null;
        TraderTileentityBase module = this.inner();
        module.setVillager(villagerItem);
        module.setWorkstation(workstation);
        this.pending.clear();
        this.trades = 0;
    }

    public List<ItemStack> waiting() {
        return this.pending;
    }

    public static ItemStack newVillagerItem(Level level, VillagerProfession profession) {
        EasyVillagerEntity made = new EasyVillagerEntity(EntityType.VILLAGER, level);
        made.setVillagerData(made.getVillagerData().setProfession(profession));
        ItemStack stack = new ItemStack(ModItems.VILLAGER.get());
        VillagerData.applyToItem(stack, made);
        return stack;
    }

    // ---- save / load ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("sccadd_module", this.module.name());
        if (this.module != Module.NONE) {
            TraderTileentityBase module = this.inner;
            if (module != null) {
                module.saveVillagerEntity();
                tag.put("sccadd_inner", module.saveCustomOnly(provider));
            } else if (this.innerData != null) {
                tag.put("sccadd_inner", this.innerData);
            }
        }
        tag.putString("sccadd_output", this.output.name());
        tag.putString("sccadd_state", this.state);
        ListTag list = new ListTag();
        for (ItemStack stack : this.pending) {
            list.add(stack.save(provider));
        }
        tag.put("sccadd_pending", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        try {
            this.module = Module.valueOf(tag.getString("sccadd_module"));
        } catch (IllegalArgumentException unknown) {
            this.module = Module.NONE;
        }
        this.inner = null;
        this.innerData = this.module != Module.NONE && tag.contains("sccadd_inner") ? tag.getCompound("sccadd_inner") : null;
        try {
            this.output = Output.valueOf(tag.getString("sccadd_output"));
        } catch (IllegalArgumentException unknown) {
            this.output = Output.NETWORK;
        }
        this.state = tag.contains("sccadd_state") ? tag.getString("sccadd_state") : "no module";
        this.pending.clear();
        ListTag list = tag.getList("sccadd_pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(provider, list.getCompound(i));
            if (!stack.isEmpty()) {
                this.pending.add(stack);
            }
        }
    }
}
