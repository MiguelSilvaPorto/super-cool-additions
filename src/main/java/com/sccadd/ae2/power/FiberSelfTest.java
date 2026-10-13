package com.sccadd.ae2.power;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import com.google.common.collect.Iterables;
import com.sccadd.SccAddConfig;
import de.scc.calculator.SccLog;
import de.scc.calculator.network.DistributorScan;
import de.scc.calculator.network.SccRegistry;
import de.scc.calculator.network.SccStorageBlockEntity;
import de.scc.calculator.network.StorageTier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /sccadd fibertest [keep]}: checks the Quartz Fiber power bridge on a live
 * server, on a small rig built in front of the command source.
 *
 * <p>It answers three questions, and says out loud which one fails:</p>
 * <ol>
 *   <li>Does the distributor still aim at AE2 blocks that accept FE? (The mixin
 *       must say no with the option on - and yes with it off, otherwise the test
 *       is not testing anything.)</li>
 *   <li>Does an AE2 cable bus without a fiber power an SCC block? (It must not.)
 *       And with a Quartz Fiber on it? (It must.)</li>
 *   <li>Does ME power really arrive in the SCC network? (A creative energy cell
 *       feeds the AE2 side; after a couple of seconds the basic energy storage of
 *       the test network must have FE in it.)</li>
 * </ol>
 *
 * <p>A diagnostic for operators and for development, not a feature. The rig is
 * removed afterwards unless {@code keep} is given (5x2x3 blocks above the source).</p>
 */
public final class FiberSelfTest {
    private static final List<Pending> PENDING = new ArrayList<>();
    /** How many ticks the power flow gets before the result is read. */
    private static final int WAIT_TICKS = 60;
    /** Hard limit while waiting for the AE2 side to come up (nodes are created on the chunk tick). */
    private static final int MAX_WAIT_TICKS = 200;

    private record Pending(ServerLevel level, BlockPos origin, BlockPos storage, CommandSourceStack source,
                           long atTick, boolean keep) {
    }

    private FiberSelfTest() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FiberSelfTest::commands);
        NeoForge.EVENT_BUS.addListener(FiberSelfTest::tick);
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sccadd")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("fibertest")
                        .executes(context -> start(context.getSource(), null, false))
                        .then(Commands.literal("keep")
                                .executes(context -> start(context.getSource(), null, true)))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> start(context.getSource(),
                                        BlockPosArgument.getBlockPos(context, "pos"), false))
                                .then(Commands.literal("keep")
                                        .executes(context -> start(context.getSource(),
                                                BlockPosArgument.getBlockPos(context, "pos"), true))))));
    }

    private static int start(CommandSourceStack source, @Nullable BlockPos where, boolean keep) {
        ServerLevel level = source.getLevel();
        BlockPos origin = where != null ? where : BlockPos.containing(source.getPosition()).above(4);
        // AE2 defers block entity initialisation (and so the creation of its grid nodes) to the
        // chunk tick. A chunk nobody is near does not tick, and the rig would never get its nodes:
        // force the test chunk for as long as the rig lives.
        level.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, true);
        clear(level, origin);

        BlockPos busPos = origin;
        BlockPos cellPos = origin.west();
        BlockPos cablePos = origin.east();
        BlockPos storagePos = origin.east(2);
        BlockPos controllerPos = origin.east().above();
        BlockPos acceptorPos = origin.above();

        // The SCC test network: a cable with a basic energy storage and a controller.
        level.setBlockAndUpdate(cablePos, SccRegistry.CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(storagePos, SccRegistry.ENERGY_STORAGES.get(StorageTier.BASIC).get().defaultBlockState());
        level.setBlockAndUpdate(controllerPos, SccRegistry.CALCULATOR_CONTROLLER.get().defaultBlockState());
        // The AE2 side: a cable bus with a cable (the fiber comes later), powered by a creative cell.
        level.setBlockAndUpdate(cellPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(busPos, AEBlocks.CABLE_BUS.block().defaultBlockState());
        if (!(level.getBlockEntity(busPos) instanceof IPartHost host)) {
            return fail(source, "could not build the AE2 cable bus");
        }
        host.addPart(AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT), null, null);

        // 1) the distributor must not aim at AE2 blocks that accept FE
        level.setBlockAndUpdate(acceptorPos, AEBlocks.ENERGY_ACCEPTOR.block().defaultBlockState());
        BlockEntity acceptor = level.getBlockEntity(acceptorPos);
        Object guarded = acceptor == null ? null : DistributorScan.examine(level, acceptor);
        SccAddConfig.AE2_DISTRIBUTOR_IGNORES_AE2.set(false);
        Object raw = acceptor == null ? null : DistributorScan.examine(level, acceptor);
        SccAddConfig.AE2_DISTRIBUTOR_IGNORES_AE2.set(true);
        boolean guardOk = guarded == null && raw != null;

        // 2) the SCC block only answers a node when a Quartz Fiber faces it
        IInWorldGridNodeHost sccHost = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, cablePos, null);
        boolean registered = sccHost != null;
        boolean withoutFiber = !registered || sccHost.getGridNode(Direction.WEST) == null;
        host.addPart(AEParts.QUARTZ_FIBER.get(), Direction.EAST, null);
        boolean withFiber = registered && sccHost.getGridNode(Direction.WEST) != null;

        // AE2 side diagnostics are read after the wait: right now AE2 has not even created its
        // grid nodes yet (it does that on the chunk tick).
        StringBuilder out = new StringBuilder("fibertest: ");
        out.append("distributor guard ").append(guardOk ? "ok" : "FAILED")
                .append(" (ignored=").append(guarded == null).append(", without option=").append(raw != null).append(")");
        out.append("; SCC block host ").append(registered ? "registered" : "MISSING");
        out.append("; without fiber ").append(withoutFiber ? "no node (ok)" : "NODE (FAILED, an AE2 cable alone must not feed it)");
        out.append("; with fiber ").append(withFiber ? "node (ok)" : "NO NODE (FAILED)");
        out.append("; waiting ").append(WAIT_TICKS).append(" ticks for the power");
        source.sendSuccess(() -> Component.literal(out.toString()), true);

        PENDING.add(new Pending(level, origin, storagePos, source,
                level.getGameTime() + WAIT_TICKS, keep));
        return guardOk && registered && withoutFiber && withFiber ? 1 : 0;
    }

    private static void tick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext();) {
            Pending pending = it.next();
            if (pending.level().getGameTime() < pending.atTick()) {
                continue;
            }
            // Wait for the AE2 side: it creates its grid nodes on the chunk tick, and in a chunk
            // that only just started ticking that can take a moment. Report early when the power
            // arrived, give up after the hard limit.
            if (stored(pending) <= 0L && pending.level().getGameTime() < pending.atTick() + MAX_WAIT_TICKS) {
                continue;
            }
            it.remove();
            report(pending);
        }
    }

    private static long stored(Pending pending) {
        return pending.level().getBlockEntity(pending.storage()) instanceof SccStorageBlockEntity be
                && be.energyHandler() != null ? be.energyHandler().getEnergyStored() : -1L;
    }

    private static void report(Pending pending) {
        ServerLevel level = pending.level();
        SccStorageBlockEntity storage = level.getBlockEntity(pending.storage()) instanceof SccStorageBlockEntity be
                ? be : null;
        IEnergyStorage energy = storage == null ? null : storage.energyHandler();
        long stored = energy == null ? -1L : energy.getEnergyStored();
        String where = pending.storage().toShortString();
        BlockPos bus = pending.origin();
        IInWorldGridNodeHost sccHost = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                pending.origin().east(), null);
        String ae = level.getBlockEntity(bus) instanceof IPartHost host
                ? ae2State(level, bus.west(), bus, host, sccHost)
                : "AE2 state unavailable";
        String text = (stored > 0L
                ? "fibertest: ME power arrived - the energy storage at " + where + " holds " + stored + " FE"
                : "fibertest: FAILED - nothing arrived at " + where + " (stored=" + stored + ")")
                + " | " + ae;
        pending.source().sendSuccess(() -> Component.literal(text), true);
        SccLog.info("sccadd", text);
        if (!pending.keep()) {
            clear(level, pending.origin());
            level.setChunkForced(pending.origin().getX() >> 4, pending.origin().getZ() >> 4, false);
        } else {
            pending.source().sendSuccess(() -> Component.literal(
                    "fibertest: rig kept around " + pending.origin().toShortString()
                            + " (chunk forced so the AE2 nodes live)"), true);
        }
    }

    private static int fail(CommandSourceStack source, String what) {
        source.sendFailure(Component.literal("fibertest: " + what));
        return 0;
    }

    /** What the AE2 side of the rig looks like: the node of the SCC block and the ME grid it should pull from. */
    private static String ae2State(ServerLevel level, BlockPos cellPos, BlockPos busPos, IPartHost host,
                                   @Nullable IInWorldGridNodeHost sccHost) {
        IGridNode our = sccHost == null ? null : sccHost.getGridNode(Direction.WEST);
        IGrid ourGrid = our == null ? null : our.getGrid();
        IPart fiber = host.getPart(Direction.EAST);
        IGridNode outer = fiber == null ? null : fiber.getExternalFacingNode();
        IGrid outerGrid = outer == null ? null : outer.getGrid();
        IGridNode cable = GridHelper.getExposedNode(level, busPos, Direction.WEST);
        IGrid cableGrid = cable == null ? null : cable.getGrid();
        IGridNode cell = GridHelper.getExposedNode(level, cellPos, Direction.EAST);
        IGrid cellGrid = cell == null ? null : cell.getGrid();
        long ourDraw = ourGrid == null ? -1L
                : (long) ourGrid.getEnergyService().extractAEPower(1.0, Actionable.SIMULATE, PowerMultiplier.ONE);
        long cellDraw = cellGrid == null ? -1L
                : (long) cellGrid.getEnergyService().extractAEPower(1.0, Actionable.MODULATE, PowerMultiplier.ONE);
        return "AE2: our node conns=" + (our == null ? "-" : Iterables.size(our.getConnections()))
                + " (grid " + (ourGrid == null ? "none" : Iterables.size(ourGrid.getNodes()) + " nodes, draw=" + ourDraw) + ")"
                + "; fiber outer " + (outer == null ? "none" : "ok, grid "
                        + (outerGrid == null ? "none" : Iterables.size(outerGrid.getNodes()) + " nodes"))
                + "; cable " + (cableGrid == null ? "none" : "grid " + Iterables.size(cableGrid.getNodes()) + " nodes")
                + "; cell " + (cellGrid == null ? "none" : "grid " + Iterables.size(cellGrid.getNodes()) + " nodes, draw=" + cellDraw);
    }

    /** Free the small rig area (5x2x3 blocks: origin-1..origin+3 in x, 0..1 in y, -1..1 in z). */
    private static void clear(ServerLevel level, BlockPos origin) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -1; x <= 3; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlockAndUpdate(pos, air);
                    }
                }
            }
        }
    }
}
