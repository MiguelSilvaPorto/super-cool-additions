package com.sccadd.ae2.power;

import com.sccadd.SccAddConfig;
import de.scc.calculator.SccLog;
import de.scc.calculator.SccServerConfig;
import de.scc.calculator.network.NetworkBlockEntity;
import de.scc.calculator.network.NetworkView;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * One {@link FiberPower} per SCC network block, created on demand (the AE2 asks for
 * a node) and dropped with the block. The tick feeds every SCC network that has at
 * least one Quartz Fiber on it.
 *
 * <p>Two rules keep the bridge honest:</p>
 * <ul>
 *   <li>It only replaces what the network actually consumed since the last tick
 *       (plus a fill of finite buffers and a floor so a starved network can start).
 *       The fiber can therefore never be used to pile energy up out of nowhere.</li>
 *   <li>Several fibers on the same network share one accounting (the level of the
 *       network is measured once per tick), so they cannot each replace the same
 *       consumption.</li>
 * </ul>
 */
public final class FiberPowers {
    private static final Map<NetworkBlockEntity, FiberPower> FIBERS = new WeakHashMap<>();
    /** Level of each SCC network at the end of the last tick (weak: views come and go). */
    private static final Map<NetworkView, Long> LAST_LEVEL = new WeakHashMap<>();
    private static long lastLog;

    private FiberPowers() {
    }

    /** The AE2 node host of an SCC block (capability registration). */
    public static synchronized Object host(NetworkBlockEntity be) {
        return fiber(be);
    }

    /** The block is gone: drop its node so AE2 lets the grid go. */
    public static synchronized void removed(NetworkBlockEntity be) {
        FiberPower fiber = FIBERS.remove(be);
        if (fiber != null) {
            fiber.destroy();
        }
    }

    private static FiberPower fiber(NetworkBlockEntity be) {
        return FIBERS.computeIfAbsent(be, FiberPower::new);
    }

    /** Called every server tick: every SCC network with a fiber on it gets what it used. */
    public static void serverTick(ServerTickEvent.Post event) {
        if (!SccAddConfig.ae2FiberPower()) {
            return;
        }
        if (!SccServerConfig.get().power) {
            // With SCC power off nothing needs power - and the antenna hands AE out for
            // free in that mode, so turning it into FE would create energy.
            log("idle: SCC power is off in scc-server.json");
            return;
        }
        Map<NetworkView, List<FiberPower>> groups = new IdentityHashMap<>();
        synchronized (FiberPowers.class) {
            for (FiberPower fiber : new ArrayList<>(FIBERS.values())) {
                if (!fiber.created()) {
                    continue;
                }
                if (!fiber.touchesFiber()) {
                    // Last fiber gone: drop the node, AE2 drops the link, nothing flows.
                    fiber.destroy();
                    log("at " + fiber.where() + ": node dropped, no Quartz Fiber faces the block");
                    continue;
                }
                NetworkView view = fiber.view();
                if (view != null) {
                    groups.computeIfAbsent(view, key -> new ArrayList<>()).add(fiber);
                }
            }
        }
        if (groups.isEmpty()) {
            int created = 0;
            synchronized (FiberPowers.class) {
                for (FiberPower fiber : FIBERS.values()) {
                    if (fiber.created()) {
                        created++;
                    }
                }
            }
            log("idle: " + FIBERS.size() + " fiber node host(s), " + created
                    + " with an AE2 node; no SCC network with a connected Quartz Fiber");
            return;
        }
        for (Map.Entry<NetworkView, List<FiberPower>> group : groups.entrySet()) {
            transfer(group.getKey(), group.getValue());
        }
    }

    private static void transfer(NetworkView view, List<FiberPower> fibers) {
        long stored = view.powerStored();
        long capacity = view.powerCapacity();
        long room = capacity > stored ? capacity - stored : 0L;
        Long previous = LAST_LEVEL.put(view, stored);
        long consumed = previous == null ? 0L : Math.max(0L, previous - stored);

        long want;
        if (room > 0L && capacity < FiberPower.FINITE_LIMIT) {
            // A real buffer: keep it full (what a generator would do).
            want = Math.max(consumed, Math.min(room, FiberPower.MAX_PER_TICK));
        } else if (stored < FiberPower.FLOOR) {
            // Endless storage, or a network with nothing in it: get it running.
            want = FiberPower.FLOOR - stored;
        } else {
            // Endless storage and already running: only replace what was used, so the
            // fiber can never drain the ME network into a buffer that never fills.
            want = consumed;
        }
        long cap = SccAddConfig.ae2FiberFePerTick();
        if (cap > 0L) {
            want = Math.min(want, cap);
        }
        want = Math.min(want, FiberPower.MAX_PER_TICK);
        if (want <= 0L) {
            return;
        }

        long got = 0L;
        for (FiberPower fiber : fibers) {
            got += fiber.deliver(view, want - got);
            if (got >= want) {
                break;
            }
        }
        log("network: stored=" + stored + " capacity=" + capacity + " consumed=" + consumed
                + " want=" + want + " got=" + got + " | " + fibers.get(0).reason());
    }

    /** One diagnostic line every 5 s, only with {@code [ae2] fiberDebug} on. */
    private static void log(String text) {
        if (!SccAddConfig.ae2FiberDebug()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastLog < 5_000L) {
            return;
        }
        lastLog = now;
        SccLog.info("sccadd", "fiber: " + text);
    }
}
