package com.sccadd.breaking;

import de.scc.calculator.SccLog;

/**
 * Counters for the batch mode. Two windows: the last 10 s (written to the SCC log while profiling is
 * on) and a lifetime total for {@code /sccadd breaking}.
 */
public final class BreakStats {
    private static long batches;
    private static long batchedBlocks;
    private static long creditBlocks;
    private static long learnedBlocks;
    private static long fallbackNoModel;
    private static long fallbackNoRoom;
    private static long bufferedItems;
    private static long deferredCalls;
    private static long lastLog = System.currentTimeMillis();

    private static long lifeBatches;
    private static long lifeBlocks;
    private static long lifeReal;
    private static long lifeDeferred;
    private static long lifeSlowLoot;
    private static long lifeWorkNanos;
    private static long lifeWorkCalls;

    private BreakStats() {
    }

    public static void batch(int blocks) {
        batches++;
        batchedBlocks += blocks;
        lifeBatches++;
        lifeBlocks += blocks;
    }

    public static void credit() {
        creditBlocks++;
    }

    public static void learn() {
        learnedBlocks++;
        lifeReal++;
    }

    public static void noModel() {
        fallbackNoModel++;
    }

    public static void noRoom() {
        fallbackNoRoom++;
    }

    public static void deferred() {
        deferredCalls++;
        lifeDeferred++;
    }

    public static void buffered(long items) {
        bufferedItems += items;
    }

    /** One loot roll went over the slow-loot threshold. */
    public static void slowLoot() {
        lifeSlowLoot++;
    }

    /** Time one work call really took. */
    public static void worked(long nanos) {
        if (nanos > 0L) {
            lifeWorkNanos += nanos;
            lifeWorkCalls++;
        }
    }

    /** One line for {@code /sccadd breaking}: what the batch mode has done since the server started. */
    public static String summary() {
        StringBuilder out = new StringBuilder();
        out.append(lifeBatches).append(" batch(es), ").append(lifeBlocks).append(" block(s) batched, ");
        long real = lifeReal;
        out.append(real).append(" real break(s)");
        if (lifeBlocks + real > 0L) {
            out.append(" (").append(String.format(java.util.Locale.ROOT, "%.1f", 100.0 * real / (lifeBlocks + real))).append("%)");
        }
        out.append(", ").append(lifeDeferred).append(" wait(s)").append(", ").append(lifeSlowLoot).append(" slow loot roll(s)");
        if (lifeWorkCalls > 0L) {
            out.append(", ").append(String.format(java.util.Locale.ROOT, "%.1f", lifeWorkNanos / 1000.0 / lifeWorkCalls)).append(" us per work call");
        }
        return out.toString();
    }

    public static void maybeLog() {
        long now = System.currentTimeMillis();
        if (now - lastLog < 10_000L) {
            return;
        }
        lastLog = now;
        if (batches + learnedBlocks + fallbackNoModel + fallbackNoRoom + deferredCalls == 0L) {
            return;
        }
        SccLog.info("sccadd", "breaking batch (last 10 s): " + batches + " batch(es) covering " + batchedBlocks
                + " block(s), " + creditBlocks + " credited, " + learnedBlocks + " real break(s) feeding the dice tables, "
                + fallbackNoModel + " blocks still learning, " + fallbackNoRoom + " blocked for lack of room, "
                + bufferedItems + " item(s) sent to the buffers, " + deferredCalls + " call(s) held back for a bigger batch");
        deferredCalls = 0;
        batches = 0;
        batchedBlocks = 0;
        creditBlocks = 0;
        learnedBlocks = 0;
        fallbackNoModel = 0;
        fallbackNoRoom = 0;
        bufferedItems = 0;
    }
}
