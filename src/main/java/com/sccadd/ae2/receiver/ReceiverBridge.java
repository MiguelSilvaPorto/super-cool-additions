package com.sccadd.ae2.receiver;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import com.sccadd.SccAddConfig;
import com.sccadd.ae2.SccAe2Delivery;
import com.sccadd.ae2.SccAe2PatternStore;
import com.sccadd.ae2.SccAe2Patterns;
import com.sccadd.ae2.SccAe2Recipe;
import com.sccadd.ae2.SccMeStorage;
import de.scc.calculator.SccLog;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.FuelOrders;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.SccRegistry;
import de.scc.calculator.network.WirelessReceiverBlockEntity;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The wireless receiver as a full AE2 bridge - the counterpart of SCC's Network
 * Antenna, without the antenna.
 *
 * <p>The receiver talks to the AE2 grid it touches: it mounts the SCC network as
 * ME storage (terminals and Storage Buses see everything), offers the recipes SCC
 * can build as real patterns (AE2 shows the plan and asks for confirmation), takes
 * the pushed ingredients and hands the finished items back to the grid.</p>
 */
public final class ReceiverBridge implements IInWorldGridNodeHost, IStorageProvider, ICraftingProvider,
        IGridNodeListener<ReceiverBridge> {
    private static final int CHECK_TICKS = 20;
    private static final int ASK_MIN_TICKS = 600;
    /** The stock moved: ask again, but not more often than this (5 s). */
    private static final int ASK_STOCK_TICKS = 100;
    private static final int PENDING_TICKS = 200;

    private final WirelessReceiverBlockEntity be;
    private final IManagedGridNode main;
    private final SccMeStorage storage;
    private Map<AEKey, StackKey> offer = Map.of();
    private Set<AEKey> emitted = Set.of();
    /** An offer answer waits here until its recipes arrive, so AE2 sees both at once. */
    private Map<AEKey, StackKey> pendingOffer;
    private long pendingSince;
    private long lastStockVersion = Long.MIN_VALUE;
    private int timer;
    private long askedAt = Long.MIN_VALUE;
    private long lastPatternLog;
    private long seenPatternVersion;
    private boolean created;

    ReceiverBridge(WirelessReceiverBlockEntity be) {
        this.be = be;
        this.storage = new SccMeStorage(be);
        this.main = GridHelper.createManagedNode(this, this)
                .setInWorldNode(true)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(0.0)
                .setVisualRepresentation(new ItemStack(SccRegistry.WIRELESS_RECEIVER.get()))
                .addService(IStorageProvider.class, this)
                .addService(ICraftingProvider.class, this);
    }

    @Nullable
    IGridNode node() {
        this.ensureCreated();
        return this.main.getNode();
    }

    @Nullable
    private ReceiverMeConnector meConnector;
    private boolean meWasActive;

    /** The receiver's ME view as an SCC storage, or null when it touches no active grid. */
    @Nullable
    ReceiverMeConnector meConnector() {
        if (this.meConnector == null && this.be.getLevel() != null) {
            this.meConnector = new ReceiverMeConnector(this.be, this);
        }
        return this.meConnector;
    }

    void ensureCreated() {
        if (!this.created && this.be.getLevel() != null && !this.be.getLevel().isClientSide) {
            this.created = true;
            this.main.create(this.be.getLevel(), this.be.getBlockPos());
        }
    }

    void destroy() {
        if (this.created) {
            this.created = false;
            this.main.destroy();
        }
    }

    @Override
    public IGridNode getGridNode(Direction dir) {
        this.ensureCreated();
        return this.main.getNode();
    }

    @Override
    public void onSaveChanges(ReceiverBridge node, IGridNode gridNode) {
        this.be.setChanged();
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        mounts.mount(this.storage);
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        List<IPatternDetails> patterns = SccAe2PatternStore.patterns(this.be);
        if (SccAddConfig.ae2DebugOffer()) {
            long now = System.currentTimeMillis();
            if (now - this.lastPatternLog >= 5_000L) {
                this.lastPatternLog = now;
                SccLog.info("sccadd", "receiver bridge at " + this.be.getBlockPos().toShortString()
                        + ": AE2 asked for patterns -> " + patterns.size());
            }
        }
        return patterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        Boolean result = SccAe2Patterns.push(this.be, SccAe2PatternStore.recipes(this.be), pattern, inputs);
        if (SccAddConfig.ae2DebugOffer()) {
            String what = pattern.getPrimaryOutput() == null
                    ? "?"
                    : pattern.getPrimaryOutput().what().getDisplayName().getString();
            SccLog.info("sccadd", "receiver bridge at " + this.be.getBlockPos().toShortString()
                    + ": AE2 pushed a pattern for " + what + " -> " + (result != null && result ? "accepted" : "refused"));
        }
        return result != null && result;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public Set<AEKey> getEmitableItems() {
        return this.emitted;
    }

    void tick() {
        com.sccadd.ae2.BridgeGuardWatchdog.check();
        Level level = this.be.getLevel();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        this.ensureCreated();
        if (++this.timer % CHECK_TICKS != 0) {
            return;
        }
        IGridNode node = this.main.getNode();
        boolean active = node != null && node.isActive();
        if (active != this.meWasActive) {
            this.meWasActive = active;
            // The connector is part of the scanned network either way (it reads nothing while the
            // node is down), so only the stock snapshot is outdated - no full rescan needed.
            NetworkView changed = this.be.network();
            if (changed != null) {
                changed.markStale();
            }
        }
        if (!active) {
            return;
        }
        ICraftingService crafting = node.getGrid().getCraftingService();
        if (crafting.getCpus().isEmpty()) {
            return;
        }
        // Recipes arrived in chunks? Tell AE2 to re-query the patterns.
        long version = SccAe2PatternStore.version(this.be);
        if (version != this.seenPatternVersion) {
            this.seenPatternVersion = version;
            ICraftingProvider.requestUpdate(this.main);
        }
        NetworkView view = this.be.network();
        if (view == null || !view.online()) {
            return;
        }
        long now = server.getGameTime();
        if (this.pendingOffer != null && now - this.pendingSince >= PENDING_TICKS) {
            // The recipes never came (client gone?) - do not hold the offer back forever.
            this.patternsArrived("");
        }
        boolean stockMoved = false;
        if (this.askedAt != Long.MIN_VALUE && now - this.askedAt >= ASK_STOCK_TICKS) {
            long stockVersion = view.stock(now, false).version();
            stockMoved = stockVersion != this.lastStockVersion;
        }
        if (this.askedAt == Long.MIN_VALUE || now < this.askedAt || stockMoved || now - this.askedAt >= ASK_MIN_TICKS) {
            StackKey anchor = StackKey.of(new ItemStack(SccRegistry.WIRELESS_RECEIVER.get()));
            if (anchor != null && FuelOrders.request(server, this.be.getBlockPos(), view, anchor, 1, FuelOrders.AE2_OFFER_ROW)) {
                this.askedAt = now;
                this.lastStockVersion = view.stock(now, false).version();
                SccLog.info("sccadd", "receiver bridge at " + this.be.getBlockPos().toShortString()
                        + ": asking what SCC can build (linked=" + this.be.linked()
                        + ", transmitter=" + (this.be.transmitter() != null)
                        + ", storages=" + view.storages().size()
                        + ", terminals=" + view.terminals().size()
                        + ", contents=" + view.contents().size() + ")");
            }
        }
        this.service(server, node.getGrid(), crafting, view);
    }

    void offered(@Nullable String text, ServerLevel where) {
        Map<AEKey, StackKey> next = new LinkedHashMap<>();
        if (text != null && !text.isBlank()) {
            for (String line : text.split("\n")) {
                if (line.startsWith("P\t")) {
                    continue;
                }
                StackKey key = StackKey.decode(line.trim());
                if (key == null) {
                    continue;
                }
                AEItemKey ae = AEItemKey.of(key.display());
                if (ae != null) {
                    next.putIfAbsent(ae, key);
                }
            }
        }
        this.offer = next;
        if (SccAddConfig.sccPatternsInAe2()) {
            // The recipes follow in their own packets: keep the current patterns and the
            // current emitted set untouched until they land, then swap both at once.
            this.pendingOffer = next;
            this.pendingSince = where.getGameTime();
        } else {
            SccAe2PatternStore.setRecipes(this.be, List.of());
            this.commit(next, List.of());
        }
    }

    void patternsArrived(String text) {
        long started = System.nanoTime();
        List<SccAe2Recipe> recipes = SccAddConfig.sccPatternsInAe2() ? SccAe2Recipe.parse(text) : List.of();
        long before = SccAe2PatternStore.version(this.be);
        SccAe2PatternStore.setRecipes(this.be, recipes);
        if (SccAe2PatternStore.version(this.be) != before) {
            // Build the AE2 patterns now and tell AE2 right away. Waiting for the periodic
            // tick check made the new recipes show up ~20 s late (and never without a CPU).
            SccAe2PatternStore.patterns(this.be);
            this.seenPatternVersion = SccAe2PatternStore.version(this.be);
            ICraftingProvider.requestUpdate(this.main);
        }
        if (SccAddConfig.ae2DebugOffer()) {
            SccLog.info("sccadd", "receiver bridge at " + this.be.getBlockPos().toShortString() + ": " + recipes.size()
                    + " recipe(s) parsed + built + announced to AE2 in " + (System.nanoTime() - started) / 1_000_000L + " ms");
        }
        Map<AEKey, StackKey> next = this.pendingOffer != null ? this.pendingOffer : this.offer;
        this.pendingOffer = null;
        this.commit(next, recipes);
    }

    private void commit(Map<AEKey, StackKey> next, List<SccAe2Recipe> recipes) {
        // Items that have a real pattern are planned through it; only the rest stay opaque.
        Set<AEItemKey> patterned = new HashSet<>();
        for (SccAe2Recipe recipe : recipes) {
            AEItemKey ae = AEItemKey.of(recipe.output().display());
            if (ae != null) {
                patterned.add(ae);
            }
        }
        Set<AEKey> out = new HashSet<>();
        for (AEKey key : next.keySet()) {
            if (!patterned.contains(key)) {
                out.add(key);
            }
        }
        if (!out.equals(this.emitted)) {
            this.emitted = Set.copyOf(out);
            ICraftingProvider.requestUpdate(this.main);
        }
        SccLog.info("sccadd", "receiver bridge at " + this.be.getBlockPos().toShortString()
                + ": SCC can build " + next.size() + " item(s), " + this.emitted.size() + " opaque, "
                + recipes.size() + " pattern(s)");
    }

    /**
     * Hands finished items to waiting AE2 jobs. Covers every item the SCC network
     * actually has (plus the current offer): a job that was requested earlier keeps
     * getting served even if the item is no longer in the offer.
     */
    private void service(ServerLevel level, IGrid grid, ICraftingService crafting, NetworkView view) {
        MEStorage inventory = grid.getStorageService().getInventory();
        java.util.Set<AEKey> candidates = new java.util.HashSet<>();
        int guard = 0;
        for (StackKey key : view.contents().keySet()) {
            if (++guard > 5_000) {
                break;
            }
            AEItemKey ae = AEItemKey.of(key.display());
            if (ae != null) {
                candidates.add(ae);
            }
        }
        candidates.addAll(this.offer.keySet());
        for (AEKey key : candidates) {
            if (!(key instanceof AEItemKey item) || !crafting.isRequesting(key)) {
                continue;
            }
            StackKey wanted = this.offer.get(key);
            if (wanted == null) {
                wanted = StackKey.of(item.toStack(1));
            }
            if (wanted == null) {
                continue;
            }
            long want = crafting.getRequestedAmount(key);
            if (want > 0L) {
                SccAe2Delivery.deliver(level, inventory, view, this.be, item, wanted, want);
            }
        }
    }
}
