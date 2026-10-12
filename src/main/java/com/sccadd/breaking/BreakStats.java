package com.sccadd.breaking;

import de.scc.calculator.SccLog;

/** Counters for the batch mode, written to the SCC log every 10 s while profiling is on. */
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

    private BreakStats() {
    }

    public static void batch(int blocks) {
        batches++;
        batchedBlocks += blocks;
    }

    public static void credit() {
        creditBlocks++;
    }

    public static void learn() {
        learnedBlocks++;
    }

    public static void noModel() {
        fallbackNoModel++;
    }

    public static void noRoom() {
        fallbackNoRoom++;
    }

    public static void deferred() {
        deferredCalls++;
    }

    public static void buffered(long items) {
        bufferedItems += items;
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
                + bufferedItems + " item(s) sent to the buffers, " + deferredCalls + " call(s) deferred to pile blocks up");
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
