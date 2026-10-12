package com.sccadd.breaking;

import com.sccadd.SccAddConfig;
import net.minecraft.server.level.ServerLevel;

/**
 * A per-server-tick time budget shared by every Breaking Calculator.
 *
 * <p>SCC already caps a single {@code work()} call at 2 ms, but only after the
 * first block of the call and without looking at what the other calculators do.
 * Ten Singularity calculators therefore can stack ten times 2 ms into one tick.
 * This budget is the hard ceiling for all of them together.</p>
 */
public final class BreakingBudget {
    private static long tick = Long.MIN_VALUE;
    private static long spentNanos;

    private BreakingBudget() {
    }

    /**
     * Opens a work call. Returns the nanoseconds this call may use,
     * or 0 when the tick's total budget is already used up (the caller should
     * keep the work for the next tick).
     */
    public static long begin(ServerLevel server) {
        long now = server.getServer().getTickCount();
        if (now != tick) {
            tick = now;
            spentNanos = 0L;
        }
        long total = (long) (SccAddConfig.totalBudgetMs() * 1_000_000.0);
        long left = total - spentNanos;
        if (left <= 0L) {
            return 0L;
        }
        long perCall = (long) (SccAddConfig.budgetMs() * 1_000_000.0);
        return Math.min(perCall, left);
    }

    /** Books the time a work call really took (it may overrun its own budget). */
    public static void record(long nanos) {
        if (nanos > 0L) {
            spentNanos += nanos;
        }
    }

    /** Nanoseconds all calculators spent in the current tick so far (for reports). */
    public static long spentNanos() {
        return spentNanos;
    }
}
