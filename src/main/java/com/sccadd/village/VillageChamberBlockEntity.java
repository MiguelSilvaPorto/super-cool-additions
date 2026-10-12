package com.sccadd.village;

import com.sccadd.SccAddConfig;
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
import de.scc.calculator.network.SccTier;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
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

    /** What happens to the experience a trade gives. */
    public enum XpMode {
        /** Experience fluid into the network when a mod has one, orbs otherwise. */
        AUTO,
        /** Always orbs, as a villager gives them. */
        ORBS,
        OFF
    }

    private static final TagKey<Fluid> EXPERIENCE = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "experience"));
    private static final int MB_PER_XP = 20;
    private static final int MAX_PENDING = 64;
    private static final Field LEVEL_UP_FLAG = villagerField("increaseProfessionLevelOnUpdate");

    private Module module = Module.NONE;
    @Nullable
    private TraderTileentityBase inner;
    /** The module's saved data, until the level is there to build it from. */
    @Nullable
    private CompoundTag innerData;
    private Output output = Output.NETWORK;
    private XpMode xpMode = XpMode.AUTO;
    private int lastUses = -1;
    private final List<ItemStack> migrated = new ArrayList<>();
    private String state = "no module";
    private final List<ItemStack> pending = new ArrayList<>();
    /** Diagnostics only: trades cost nothing and nothing is taken from a network. */
    private boolean freeTrades;
    private int trades;
    /** The "keep this much in stock" targets, keyed by the item a trade sells (its StackKey code). */
    private final Map<String, Integer> keeps = new LinkedHashMap<>();
    /** Trades the tier's speed still owes this second. */
    private double keepAllowance;
    /** The villagers the block itself keeps, up to the tier's cap. */
    private final List<Stored> stock = new ArrayList<>();
    private int nextVillagerId;

    /** A villager kept in the block. */
    private record Stored(int id, ItemStack stack) {
    }

    public VillageChamberBlockEntity(BlockPos pos, BlockState state) {
        super(VillageRegistry.CHAMBER_BE.get(), pos, state);
    }

    // ---- the module ----

    public Module module() {
        return this.module;
    }

    /** Which SCC tier this chamber is. Everything a tier changes starts here. */
    public SccTier tier() {
        return this.getBlockState().getBlock() instanceof VillageChamberBlock chamber ? chamber.tier() : SccTier.BASIC;
    }

    // ---- what the tier changes ----

    /** How many villagers the block itself keeps. */
    public int villagerCap() {
        return switch (this.tier()) {
            case BASIC -> 1;
            case RED_DWARF -> 2;
            case YELLOW_STAR -> 4;
            case BLUE_GIANT -> 8;
            case NEUTRON_STAR -> 16;
            case SINGULARITY -> 32;
        };
    }

    /** How many trades may be kept stocked at once. */
    public int keepEntries() {
        return switch (this.tier()) {
            case BASIC -> 1;
            case RED_DWARF -> 2;
            case YELLOW_STAR -> 4;
            case BLUE_GIANT -> 8;
            case NEUTRON_STAR -> 16;
            case SINGULARITY -> 32;
        };
    }

    /** The most one trade may keep in stock. */
    public int keepCap() {
        return switch (this.tier()) {
            case BASIC -> 1_000;
            case RED_DWARF -> 10_000;
            case YELLOW_STAR -> 25_000;
            case BLUE_GIANT -> 50_000;
            case NEUTRON_STAR -> 75_000;
            case SINGULARITY -> 99_999;
        };
    }

    /** How many trades a second the tier may do to keep the stock up. */
    public int tradesPerSecond() {
        return this.tier().parallelJobs;
    }

    /** What one kept trade costs in energy: the Singularity charges, the rest are free. */
    public long tradeEnergy() {
        return this.tier() == SccTier.SINGULARITY && SccAddConfig.villageEnergy() ? SccAddConfig.villageEnergyCost() : 0L;
    }

    /** What a new villager in the stock costs: the Singularity charges, the rest are free. */
    public long villagerEnergy() {
        return this.tier() == SccTier.SINGULARITY && SccAddConfig.villageEnergy() ? SccAddConfig.villageEnergyCost() : 0L;
    }

    // ---- keeping trades stocked ----

    /** The keep target of each offer, in the offers' order: what the window shows and edits. */
    public List<Integer> keepTargets(List<MerchantOffer> offers) {
        List<Integer> out = new ArrayList<>(offers.size());
        for (MerchantOffer offer : offers) {
            Integer target = this.keeps.get(StackKey.of(offer.getResult()).encode());
            out.add(target == null ? 0 : target);
        }
        return out;
    }

    /** Sets (0 clears) how much of one offer's result the network should always hold. */
    public void setKeep(int offerIndex, int amount, @Nullable Player player) {
        MerchantOffers offers = this.offers();
        if (offerIndex < 0 || offerIndex >= offers.size()) {
            return;
        }
        String code = StackKey.of(offers.get(offerIndex).getResult()).encode();
        int wanted = Math.max(0, Math.min(this.keepCap(), amount));
        if (wanted == 0) {
            this.keeps.remove(code);
        } else if (!this.keeps.containsKey(code) && this.keeps.size() >= this.keepEntries()) {
            this.say(player, "This tier keeps " + this.keepEntries() + " trade(s) at once - upgrade for more.");
            return;
        } else {
            this.keeps.put(code, wanted);
        }
        this.changed();
    }

    /** The kept trades run on their own at the tier's speed, paid from the network, up to the wanted stock. */
    private void keepStock() {
        if (this.keeps.isEmpty() || this.level == null) {
            return;
        }
        TraderTileentityBase module = this.inner();
        if (module == null || !module.hasVillager() || !module.hasWorkstation()) {
            return;
        }
        MerchantOffers offers = this.offers();
        if (offers.isEmpty()) {
            return;
        }
        NetworkView view = this.freeTrades ? null : this.network();
        if (!this.freeTrades && (view == null || !view.online())) {
            return;
        }
        this.keepAllowance = Math.min(this.keepAllowance + this.tradesPerSecond() / 20.0, this.tradesPerSecond());
        while (this.keepAllowance >= 1.0) {
            if (!this.keepOne(offers, view)) {
                this.keepAllowance = 0.0;
                return;
            }
            this.keepAllowance -= 1.0;
        }
    }

    /** One kept trade: paid from the network, the result where the output says. */
    private boolean keepOne(MerchantOffers offers, @Nullable NetworkView view) {
        for (Map.Entry<String, Integer> entry : this.keeps.entrySet()) {
            StackKey key = StackKey.decode(entry.getKey());
            if (key == null || key.item() == null) {
                continue;
            }
            long have = view == null ? 0L : view.count(key);
            if (have >= entry.getValue()) {
                continue;
            }
            for (MerchantOffer offer : offers) {
                if (offer.isOutOfStock() || !StackKey.of(offer.getResult()).equals(key)) {
                    continue;
                }
                if (!this.freeTrades) {
                    ItemStack a = offer.getCostA();
                    ItemStack b = offer.getCostB();
                    if (!has(view, a) || !has(view, b) || !this.drawTradeEnergy(view)) {
                        continue;
                    }
                    if (!take(view, a) || !take(view, b)) {
                        continue;
                    }
                }
                offer.increaseUses();
                EasyVillagerEntity villager = this.villager();
                if (villager != null) {
                    villager.setVillagerXp(villager.getVillagerXp() + offer.getXp());
                    if (shouldLevelUp(villager)) {
                        flagLevelUp(villager);
                    }
                }
                this.trades++;
                this.rewardXp(offer, view);
                // kept trades always put the result in the network: that is the stock they keep up
                ItemStack made = offer.getResult().copy();
                ItemStack left = view == null ? made : view.insert(made, false);
                if (!left.isEmpty() && (this.freeTrades || this.pending.size() < MAX_PENDING)) {
                    this.pending.add(left);
                }
                TraderTileentityBase module = this.inner();
                if (module != null) {
                    module.saveVillagerEntity();
                }
                this.changed();
                return true;
            }
        }
        return false;
    }

    /** The tier's price for a kept trade; false when the network cannot pay it. */
    private boolean drawTradeEnergy(@Nullable NetworkView view) {
        long cost = this.tradeEnergy();
        if (cost <= 0L) {
            return true;
        }
        return view != null && view.drawEnergy(cost, false) >= cost;
    }

    /** The tier's price for a new villager in the stock; false when the network cannot pay it. */
    private boolean payVillager(@Nullable NetworkView view, @Nullable Player player) {
        long cost = this.villagerEnergy();
        if (cost <= 0L || this.freeTrades) {
            return true;
        }
        if (view == null || view.drawEnergy(cost, false) < cost) {
            this.say(player, "This tier charges " + cost + " FE for a villager and the network cannot pay.");
            return false;
        }
        return true;
    }

    // ---- the villagers the block keeps ----

    /** How many villagers the block keeps right now. */
    public int stored() {
        return this.stock.size();
    }

    /** Puts a villager item in the block's stock, paying the tier's price; false when full or unpaid. */
    private boolean stockVillager(ItemStack villager, @Nullable NetworkView view, @Nullable Player player) {
        if (this.stock.size() >= this.villagerCap()) {
            this.say(player, "This tier keeps " + this.villagerCap() + " villager(s) - upgrade for more.");
            return false;
        }
        if (!this.payVillager(view, player)) {
            return false;
        }
        this.stock.add(new Stored(this.nextVillagerId++, villager.copyWithCount(1)));
        return true;
    }

    /** The kept villager a window code points at, or null when the code is not one of ours. */
    @Nullable
    private Stored find(String code) {
        if (code == null || code.length() < 2 || code.charAt(0) != 'v') {
            return null;
        }
        int id;
        try {
            id = Integer.parseInt(code.substring(1));
        } catch (NumberFormatException notOurs) {
            return null;
        }
        for (Stored stored : this.stock) {
            if (stored.id() == id) {
                return stored;
            }
        }
        return null;
    }

    /** The Easy Villagers block entity inside, built on first use. */
    @Nullable
    public TraderTileentityBase inner() {
        if (this.module == Module.NONE || this.level == null) {
            return null;
        }
        if (this.inner == null) {
            BlockPos pos = this.getBlockPos();
            Direction facing = this.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    ? this.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
            this.inner = this.module == Module.AUTO
                    ? new AutoTraderTileentity(pos, ModBlocks.AUTO_TRADER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing))
                    : new TraderTileentity(pos, ModBlocks.TRADER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
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
            if (villager.getOffers().isEmpty()) {
                // saved as an empty list (a villager never traded with): make them from the profession's tables
                resetOffers(villager);
                villager.getOffers();
            }
            module.saveVillagerEntity();
            this.setChanged();
            this.sync();
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
            this.rewardXp(offer, view);
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

    // ---- experience ----

    public XpMode xpMode() {
        return this.xpMode;
    }

    public void setXpMode(XpMode mode) {
        this.xpMode = mode;
        this.changed();
    }

    @Nullable
    private static Fluid experienceFluid() {
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid.defaultFluidState().is(EXPERIENCE) && fluid.isSource(fluid.defaultFluidState())) {
                return fluid;
            }
        }
        return null;
    }

    /**
     * What a villager gives for a trade: experience orbs. If a mod adds an experience fluid, the
     * experience goes into the network as that fluid instead (20 mB a point), and only what the network
     * cannot take becomes orbs.
     */
    private void rewardXp(MerchantOffer offer, @Nullable NetworkView view) {
        if (!(this.level instanceof ServerLevel server) || !offer.shouldRewardExp() || this.xpMode == XpMode.OFF) {
            return;
        }
        int points = 3 + server.random.nextInt(4);
        if (this.xpMode == XpMode.AUTO && view != null && view.online()) {
            Fluid fluid = experienceFluid();
            if (fluid != null) {
                FluidStack left = view.insertFluid(new FluidStack(fluid, points * MB_PER_XP), false);
                points = left == null ? 0 : (left.getAmount() + MB_PER_XP - 1) / MB_PER_XP;
            }
        }
        if (points > 0) {
            ExperienceOrb.award(server, Vec3.atCenterOf(this.worldPosition).add(0.0, 0.3, 0.0), points);
        }
    }

    /** The Auto Trader trades on its own: every use it racks up on its offer gives the experience a trade gives. */
    private void rewardAutoTrades() {
        if (!(this.inner instanceof AutoTraderTileentity auto) || !auto.hasVillager()) {
            this.lastUses = -1;
            return;
        }
        MerchantOffer offer = auto.getOffer();
        if (offer == null) {
            this.lastUses = -1;
            return;
        }
        int uses = offer.getUses();
        if (this.lastUses >= 0 && uses > this.lastUses) {
            NetworkView view = this.freeTrades ? null : this.network();
            for (int i = this.lastUses; i < uses && i < this.lastUses + 16; i++) {
                this.trades++;
                this.rewardXp(offer, view);
            }
        }
        this.lastUses = uses;
    }

    // ---- what the window shows ----

    /** The window's content, made here so a client never has to ask a villager for anything. */
    public VillageView buildView() {
        TraderTileentityBase module = this.inner();
        EasyVillagerEntity villager = this.villager();
        String profession = "";
        int level = 1;
        int xp = 0;
        int xpFrom = 0;
        int xpTo = 0;
        boolean top = false;
        String name = "";
        List<MerchantOffer> offers = List.of();
        if (villager != null) {
            net.minecraft.world.entity.npc.VillagerData data = villager.getVillagerData();
            profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.getProfession()).getPath().replace('_', ' ');
            level = data.getLevel();
            xp = villager.getVillagerXp();
            top = !net.minecraft.world.entity.npc.VillagerData.canLevelUp(level);
            xpFrom = net.minecraft.world.entity.npc.VillagerData.getMinXpPerLevel(level);
            xpTo = net.minecraft.world.entity.npc.VillagerData.getMaxXpPerLevel(level);
            name = villager.hasCustomName() ? villager.getCustomName().getString() : "";
            offers = new ArrayList<>(this.offers());
        }
        ItemStack bench = module != null && module.hasWorkstation() ? new ItemStack(module.getWorkstation()) : ItemStack.EMPTY;
        List<VillageView.Villager> villagers = new ArrayList<>();
        List<VillageView.Bench> benches = new ArrayList<>();
        for (Stored stored : this.stock) {
            VillageView.Villager card = this.describe("v" + stored.id(), stored.stack(), 1);
            if (card != null) {
                villagers.add(card);
            }
        }
        NetworkView view = this.freeTrades || this.level == null ? null : this.network();
        if (view != null && view.online()) {
            for (java.util.Map.Entry<StackKey, Long> entry : view.contents().entrySet()) {
                StackKey key = entry.getKey();
                if (key.item() instanceof VillagerItem) {
                    VillageView.Villager card = this.card(view, key, entry.getValue());
                    if (card != null) {
                        villagers.add(card);
                    }
                } else if (key.item() instanceof BlockItem blockItem && professionFor(blockItem.getBlock()) != null
                        && module != null && module.isValidBlock(blockItem.getBlock())) {
                    benches.add(new VillageView.Bench(key.encode(), new ItemStack(blockItem), entry.getValue()));
                }
            }
        }
        return new VillageView(this.module.ordinal(), this.tier().ordinal(), villager != null, profession, level, xp, xpFrom, xpTo, top,
                name, bench, offers, this.output.ordinal(), this.xpMode.ordinal(), this.state, villagers, benches,
                this.keepTargets(offers), this.keepCap(), this.villagerCap(), this.stored(), this.tradesPerSecond(),
                (int) Math.min(Integer.MAX_VALUE, this.tradeEnergy()), (int) Math.min(Integer.MAX_VALUE, this.villagerEnergy()));
    }

    /** What the window shows of a villager item in the system: its name, profession, level and the offers it already has. */
    @Nullable
    private VillageView.Villager card(NetworkView view, StackKey key, long count) {
        ItemStack stack = view.extract(key, 1, true);
        if (stack.isEmpty()) {
            return null;
        }
        return this.describe(key.encode(), stack, (int) Math.min(count, 9999L));
    }

    /** Reads a villager item into the card the window shows. */
    @Nullable
    private VillageView.Villager describe(String code, ItemStack stack, int count) {
        if (stack.isEmpty() || this.level == null) {
            return null;
        }
        String profession = "villager";
        String professionId = "minecraft:none";
        String type = "minecraft:plains";
        int level = 1;
        String name = stack.getHoverName().getString();
        List<MerchantOffer> offers = new ArrayList<>();
        try {
            EasyVillagerEntity entity = VillagerData.createEasyVillager(stack, this.level);
            net.minecraft.world.entity.npc.VillagerData data = entity.getVillagerData();
            profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.getProfession()).getPath().replace('_', ' ');
            professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.getProfession()).toString();
            type = BuiltInRegistries.VILLAGER_TYPE.getKey(data.getType()).toString();
            level = data.getLevel();
            if (entity.hasCustomName()) {
                name = entity.getCustomName().getString();
            }
            // only the offers it has already made: asking would roll new ones every time the window looks
            Field field = net.minecraft.world.entity.npc.AbstractVillager.class.getDeclaredField("offers");
            field.setAccessible(true);
            Object made = field.get(entity);
            if (made instanceof MerchantOffers list) {
                offers.addAll(list);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // show what we have
        }
        return new VillageView.Villager(code, name, profession, professionId, type, level, count, offers);
    }

    // ---- villagers and workstations in the system: swap them in quickly ----

    /** The villager goes into the block's own stock (up to the tier's cap); the workstation goes to the system. */
    public void storeCurrent(@Nullable Player player) {
        TraderTileentityBase module = this.inner();
        if (module == null) {
            this.say(player, "Put an Easy Villagers Trader or Auto Trader in first.");
            return;
        }
        NetworkView view = this.freeTrades ? null : this.network();
        if (module.hasVillager()) {
            module.saveVillagerEntity();
            if (!this.stockVillager(module.getVillager(), view, player)) {
                return;
            }
            module.removeVillager();
        }
        if (module.hasWorkstation()) {
            Block block = module.removeWorkstation();
            if (block != null) {
                if (view != null && view.online()) {
                    ItemStack left = view.insert(new ItemStack(block), false);
                    if (!left.isEmpty()) {
                        this.giveOrDrop(player, left);
                    }
                } else {
                    this.giveOrDrop(player, new ItemStack(block));
                }
            }
        }
        this.lastUses = -1;
        this.say(player, "Villager kept in the chamber, workstation in the system.");
        this.changed();
    }

    /** To the player when there is one (the window), on the floor when there is not (the self test). */
    private void giveOrDrop(@Nullable Player player, ItemStack stack) {
        if (player != null) {
            ItemHandlerHelper.giveItemToPlayer(player, stack);
        } else if (this.level != null) {
            Block.popResource(this.level, this.worldPosition, stack);
        }
    }

    /** Puts a kept villager in the chamber; the one that was there takes its place in the stock. */
    public void useVillager(String code, @Nullable Player player) {
        TraderTileentityBase module = this.inner();
        if (module == null) {
            return;
        }
        Stored picked = this.find(code);
        if (picked == null) {
            this.useFromSystem(code, player);
            return;
        }
        NetworkView view = this.freeTrades ? null : this.network();
        ItemStack incoming = picked.stack().copyWithCount(1);
        this.stock.remove(picked);
        if (module.hasVillager()) {
            module.saveVillagerEntity();
            if (!this.stockVillager(module.getVillager(), view, player)) {
                this.stock.add(picked);
                return;
            }
            module.removeVillager();
        }
        module.setVillager(incoming);
        this.lastUses = -1;
        this.say(player, "Villager swapped in.");
        this.changed();
        this.ensureOffers();
    }

    /** The system path: a villager of the network goes in; the one there goes to the stock first. */
    private void useFromSystem(String code, @Nullable Player player) {
        TraderTileentityBase module = this.inner();
        NetworkView view = this.freeTrades ? null : this.network();
        StackKey key = StackKey.decode(code);
        if (module == null || view == null || !view.online() || key == null) {
            return;
        }
        ItemStack incoming = view.extract(key, 1, false);
        if (incoming.isEmpty()) {
            this.say(player, "That villager is no longer in the system.");
            return;
        }
        if (module.hasVillager()) {
            module.saveVillagerEntity();
            ItemStack back = module.getVillager().copy();
            if (this.stock.size() < this.villagerCap() && this.payVillager(view, player)) {
                this.stock.add(new Stored(this.nextVillagerId++, back.copyWithCount(1)));
                module.removeVillager();
            } else {
                ItemStack left = view.insert(back, false);
                if (!left.isEmpty()) {
                    view.insert(incoming, false);
                    this.say(player, "The system has no room for the villager in the chamber.");
                    return;
                }
                module.removeVillager();
            }
        }
        module.setVillager(incoming);
        this.lastUses = -1;
        this.say(player, "Villager swapped in.");
        this.changed();
        this.ensureOffers();
    }

    /** Gives a kept villager to the player (or one of the system's). */
    public void takeVillager(String code, Player player) {
        Stored stored = this.find(code);
        if (stored != null) {
            this.stock.remove(stored);
            this.giveOrDrop(player, stored.stack().copy());
            this.changed();
            return;
        }
        NetworkView view = this.freeTrades ? null : this.network();
        StackKey key = StackKey.decode(code);
        if (view == null || !view.online() || key == null) {
            return;
        }
        ItemStack out = view.extract(key, 1, false);
        if (!out.isEmpty()) {
            ItemHandlerHelper.giveItemToPlayer(player, out);
        }
    }

    /** Puts a workstation from the system in the chamber; the one that was there goes into the system. */
    public void useBench(String code, @Nullable Player player) {
        TraderTileentityBase module = this.inner();
        NetworkView view = this.freeTrades ? null : this.network();
        StackKey key = StackKey.decode(code);
        if (module == null || view == null || !view.online() || key == null
                || !(key.item() instanceof BlockItem blockItem) || !module.isValidBlock(blockItem.getBlock())) {
            return;
        }
        ItemStack incoming = view.extract(key, 1, false);
        if (incoming.isEmpty()) {
            return;
        }
        if (module.hasWorkstation()) {
            Block old = module.removeWorkstation();
            if (old != null) {
                ItemStack left = view.insert(new ItemStack(old), false);
                if (!left.isEmpty()) {
                    module.setWorkstation(old);
                    view.insert(incoming, false);
                    this.say(player, "The system has no room for the workstation in the chamber.");
                    return;
                }
            }
        }
        module.setWorkstation(blockItem.getBlock());
        this.say(player, "Workstation swapped in.");
        this.changed();
        this.ensureOffers();
    }

    /** Names the villager in the chamber. */
    public void rename(String name) {
        EasyVillagerEntity villager = this.villager();
        if (villager == null) {
            return;
        }
        String clean = name == null ? "" : name.strip();
        villager.setCustomName(clean.isEmpty() ? null : Component.literal(clean.length() > 40 ? clean.substring(0, 40) : clean));
        this.changed();
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
        if (!this.migrated.isEmpty()) {
            this.pending.addAll(this.migrated);
            this.migrated.clear();
        }
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
            this.rewardAutoTrades();
        }
        this.keepStock();
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

    private static void resetOffers(EasyVillagerEntity villager) {
        try {
            Field field = net.minecraft.world.entity.npc.AbstractVillager.class.getDeclaredField("offers");
            field.setAccessible(true);
            field.set(villager, null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // keep what it has
        }
    }

    /** Called by the window while it has no offers to show: have the server make and send them. */
    public void refresh() {
        this.ensureOffers();
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
        tag.putString("sccadd_xp", this.xpMode.name());
        tag.putString("sccadd_state", this.state);
        ListTag list = new ListTag();
        for (ItemStack stack : this.pending) {
            list.add(stack.save(provider));
        }
        tag.put("sccadd_pending", list);
        ListTag keeps = new ListTag();
        for (Map.Entry<String, Integer> entry : this.keeps.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putString("trade", entry.getKey());
            one.putInt("amount", entry.getValue());
            keeps.add(one);
        }
        tag.put("sccadd_keeps", keeps);
        ListTag stock = new ListTag();
        for (Stored stored : this.stock) {
            CompoundTag one = new CompoundTag();
            one.putInt("id", stored.id());
            one.put("villager", stored.stack().save(provider));
            stock.add(one);
        }
        tag.put("sccadd_stock", stock);
        tag.putInt("sccadd_next_villager", this.nextVillagerId);
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
        try {
            this.xpMode = XpMode.valueOf(tag.getString("sccadd_xp"));
        } catch (IllegalArgumentException unknown) {
            this.xpMode = XpMode.AUTO;
        }
        // an older build kept saved villagers inside the chamber: hand them to the system through the pending list
        ListTag saved = tag.getList("sccadd_setups", Tag.TAG_COMPOUND);
        for (int i = 0; i < saved.size(); i++) {
            CompoundTag entry = saved.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(provider, entry.getCompound("villager"));
            if (!stack.isEmpty()) {
                this.migrated.add(stack);
            }
            if (entry.contains("bench")) {
                ResourceLocation bench = ResourceLocation.tryParse(entry.getString("bench"));
                if (bench != null) {
                    BuiltInRegistries.ITEM.getOptional(bench).ifPresent(item -> this.migrated.add(new ItemStack(item)));
                }
            }
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
        this.keeps.clear();
        ListTag keeps = tag.getList("sccadd_keeps", Tag.TAG_COMPOUND);
        for (int i = 0; i < keeps.size(); i++) {
            CompoundTag one = keeps.getCompound(i);
            String trade = one.getString("trade");
            int amount = one.getInt("amount");
            if (!trade.isEmpty() && amount > 0) {
                this.keeps.put(trade, amount);
            }
        }
        this.stock.clear();
        ListTag stock = tag.getList("sccadd_stock", Tag.TAG_COMPOUND);
        for (int i = 0; i < stock.size(); i++) {
            CompoundTag one = stock.getCompound(i);
            ItemStack villager = ItemStack.parseOptional(provider, one.getCompound("villager"));
            if (!villager.isEmpty()) {
                this.stock.add(new Stored(one.getInt("id"), villager.copyWithCount(1)));
            }
        }
        this.nextVillagerId = Math.max(tag.getInt("sccadd_next_villager"), this.stock.size());
    }
}
