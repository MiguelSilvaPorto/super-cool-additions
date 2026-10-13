package com.sccadd.ae2.power;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.PowerUnit;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.util.AECableType;
import de.scc.calculator.network.NetworkBlockEntity;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.SccPowerGrid;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The AE2 side of one SCC network block: an energy-only grid node that a Quartz
 * Fiber can plug into, so the ME network can power the SCC network.
 *
 * <p>The AE2 Quartz Fiber bridges the power of the two grids it separates. It only
 * does something if the block on its far side exposes a grid node, which is what
 * this class provides: when a fiber faces the block, {@link #getGridNode} hands
 * AE2 the node, AE2 connects the fiber's outer node to it and the energy overlay
 * carries the power over. A plain AE2 cable next to the block gets no node, so the
 * link stays what AE2 made the fiber for: power only, no channels and no data.</p>
 *
 * <p>The node itself only ever <em>pulls</em>: {@link #deliver} takes AE from
 * whichever grid the node is in (the ME network, through the fiber) and hands the
 * equivalent FE to the SCC network with {@link SccPowerGrid#feed}. The ratio is
 * AE2's own (2 FE per AE by default, the same the SCC antenna uses the other way
 * around), read live from {@link PowerUnit}.</p>
 */
public final class FiberPower implements IInWorldGridNodeHost, IGridNodeListener<FiberPower> {
    /** The one AE2 part that may power an SCC block. */
    private static final ResourceLocation QUARTZ_FIBER = ResourceLocation.fromNamespaceAndPath("ae2", "quartz_fiber");

    /** Nothing moves more than this much FE in one tick (overflow guard, not a policy). */
    public static final long MAX_PER_TICK = 2_000_000_000L;
    /** A network below this much FE is topped up, so machines can start from scratch. */
    public static final long FLOOR = 100_000L;
    /** Buffers at or above this size count as endless: only the consumption is replaced. */
    public static final long FINITE_LIMIT = 1_000_000_000_000L;
    /** Id of the power source shown in the SCC controller's power panel. */
    public static final String SOURCE = "ae2";

    private final NetworkBlockEntity be;
    private final IManagedGridNode main;
    private boolean created;
    /** Why the last {@link #deliver} moved nothing (diagnostics, see {@code fiberDebug}). */
    private String reason = "never asked";

    FiberPower(NetworkBlockEntity be) {
        this.be = be;
        this.main = GridHelper.createManagedNode(this, this)
                .setInWorldNode(true)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setIdlePowerUsage(0.0)
                .setFlags(GridFlags.CANNOT_CARRY)
                .setVisualRepresentation(new ItemStack(be.getBlockState().getBlock()));
    }

    @Override
    public void onSaveChanges(FiberPower owner, IGridNode node) {
        // Energy only: the node has no state of its own to persist.
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        Level level = this.be.getLevel();
        if (level == null || level.isClientSide) {
            return null;
        }
        if (!fiberFaces(level, this.be.getBlockPos(), dir)) {
            return null;
        }
        this.ensureCreated();
        return this.main.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        Level level = this.be.getLevel();
        if (level != null && fiberFaces(level, this.be.getBlockPos(), dir)) {
            // Same answer AE2 machines give, so the cable draws its connection to us.
            return AECableType.SMART;
        }
        return AECableType.NONE;
    }

    /** True when an AE2 cable bus with a Quartz Fiber facing this block sits in {@code dir}. */
    private static boolean fiberFaces(Level level, BlockPos pos, @Nullable Direction dir) {
        if (dir == null || !level.isLoaded(pos.relative(dir))) {
            return false;
        }
        if (!(level.getBlockEntity(pos.relative(dir)) instanceof IPartHost host)) {
            return false;
        }
        IPart part = host.getPart(dir.getOpposite());
        if (part == null) {
            return false;
        }
        IPartItem<?> item = part.getPartItem();
        return item != null && QUARTZ_FIBER.equals(IPartItem.getId(item));
    }

    /** True while at least one Quartz Fiber still faces this block. */
    boolean touchesFiber() {
        Level level = this.be.getLevel();
        if (level == null) {
            return false;
        }
        BlockPos pos = this.be.getBlockPos();
        for (Direction side : Direction.values()) {
            if (fiberFaces(level, pos, side)) {
                return true;
            }
        }
        return false;
    }

    void ensureCreated() {
        if (this.created) {
            return;
        }
        Level level = this.be.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        this.created = true;
        this.main.create(level, this.be.getBlockPos());
    }

    void destroy() {
        if (this.created) {
            this.created = false;
            this.main.destroy();
        }
    }

    boolean created() {
        return this.created;
    }

    /** The SCC network this block belongs to, or null when it has none yet. */
    @Nullable
    NetworkView view() {
        Level level = this.be.getLevel();
        if (level == null || level.isClientSide) {
            return null;
        }
        NetworkView view = this.be.network();
        return view == null || view.level() == null ? null : view;
    }

    String where() {
        return this.be.getBlockPos().toShortString();
    }

    /**
     * Pulls up to {@code wantFe} worth of AE out of the grid behind this fiber and
     * gives the resulting FE to the SCC network. Returns how much really arrived.
     */
    long deliver(NetworkView view, long wantFe) {
        this.reason = "nothing asked";
        if (wantFe <= 0L) {
            return 0L;
        }
        IGridNode node = this.main.getNode();
        if (node == null) {
            this.reason = "no node";
            return 0L;
        }
        IGrid grid = node.getGrid();
        if (grid == null) {
            this.reason = "node has no grid";
            return 0L;
        }
        IEnergyService energy = grid.getEnergyService();
        double wantAe = PowerUnit.FE.convertTo(PowerUnit.AE, (double) wantFe);
        double gotAe = energy.extractAEPower(wantAe, Actionable.MODULATE, PowerMultiplier.ONE);
        this.reason = "asked " + (long) wantAe + " AE, ME gave " + (long) gotAe
                + " AE (our grid " + com.google.common.collect.Iterables.size(grid.getNodes()) + " node(s), "
                + com.google.common.collect.Iterables.size(node.getConnections()) + " connection(s))";
        if (gotAe <= 0.0) {
            return 0L;
        }
        long fe = (long) PowerUnit.AE.convertTo(PowerUnit.FE, gotAe);
        long put = SccPowerGrid.feed(view, SOURCE, fe);
        this.reason = this.reason + ", gave " + fe + " FE, network took " + put;
        if (put < fe) {
            // The network filled up in the meantime: hand the rest back to the ME.
            energy.injectPower(PowerUnit.FE.convertTo(PowerUnit.AE, (double) (fe - put)), Actionable.MODULATE);
        }
        return put;
    }

    String reason() {
        return this.reason;
    }
}
