package com.sccadd.ae2;

import de.scc.calculator.SccLog;
import de.scc.calculator.compat.BridgeGuard;
import de.scc.calculator.network.NetworkCache;

/**
 * SCC's recursion guard ({@link BridgeGuard}) is a plain static counter without
 * thread safety and without a guaranteed balance on every path. When it leaks,
 * every ME storage read returns empty forever - the connector "stops seeing" the
 * AE2 items and nothing can be crafted. This watchdog notices a guard that stays
 * held across many ticks and resets it, then forces a network rescan.
 */
public final class BridgeGuardWatchdog {
    private static final int STUCK_TICKS = 60;
    private static int stuck;

    private BridgeGuardWatchdog() {
    }

    public static void check() {
        if (!BridgeGuard.busy()) {
            stuck = 0;
            return;
        }
        if (++stuck < STUCK_TICKS) {
            return;
        }
        stuck = 0;
        int guard = 0;
        while (BridgeGuard.busy() && guard++ < 4_096) {
            BridgeGuard.leave();
        }
        NetworkCache.invalidate();
        SccLog.warn("sccadd", "the AE2 bridge guard was stuck and got reset (the ME item listing would have stayed empty)");
    }
}
