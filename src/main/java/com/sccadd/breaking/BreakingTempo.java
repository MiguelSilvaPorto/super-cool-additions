package com.sccadd.breaking;

/**
 * The batch clock of one Breaking Calculator: how many blocks the machine has earned and is still
 * owed, and how long it has been holding them back.
 *
 * <p>The machine banks its own rate (tier or Overdrive, times the speedup config) in real time and
 * spends that credit when it extracts blocks. Waiting only groups the work into bigger batches; it
 * never adds blocks the machine would not have broken anyway.</p>
 */
public final class BreakingTempo {
    /** A world tick jump (load, dimension) must not hand the machine a pile of blocks. */
    private static final long MAX_ELAPSED_TICKS = 200L;

    private double credit;
    private long creditAt = Long.MIN_VALUE;
    private long waitFrom = Long.MIN_VALUE;

    /**
     * Banks the time since the last work call and returns the credit (in blocks).
     *
     * @param now          the level's game time
     * @param ratePerTick  blocks the machine earns per tick
     * @param cap          the most that can be banked at once
     */
    public double accrue(long now, double ratePerTick, int cap) {
        if (this.creditAt == Long.MIN_VALUE || now < this.creditAt) {
            this.creditAt = now;
            this.credit = 0.0;
            return 0.0;
        }
        long elapsed = Math.min(MAX_ELAPSED_TICKS, now - this.creditAt);
        this.creditAt = now;
        if (elapsed > 0L && ratePerTick > 0.0) {
            this.credit = Math.min((double) cap, this.credit + elapsed * ratePerTick);
        }
        return this.credit;
    }

    public double credit() {
        return this.credit;
    }

    /** Whole blocks the machine has banked right now. */
    public int earned(int cap) {
        return (int) Math.max(0.0, Math.min((double) cap, Math.floor(this.credit)));
    }

    /** Blocks were taken from the network: they are paid with the credit. */
    public void delivered(int blocks) {
        if (blocks > 0) {
            this.credit = Math.max(0.0, this.credit - blocks);
        }
    }

    /** One block was broken for real (SCC's own path): it is part of the machine's rate as well. */
    public void realBreak() {
        this.delivered(1);
    }

    /**
     * Opens the waiting window (first call) or reports whether it is still allowed to grow.
     *
     * @param waitTicks the longest the window may stay open; 0 = no limit
     */
    public boolean hold(long now, int waitTicks) {
        if (this.waitFrom == Long.MIN_VALUE) {
            this.waitFrom = now;
            return true;
        }
        return waitTicks <= 0 || now - this.waitFrom < waitTicks;
    }

    /** The pile is big enough (or the window ran out): the next call delivers it. */
    public void release() {
        this.waitFrom = Long.MIN_VALUE;
    }

    /** The machine could not use what it earned (no stock, no room, table still learning). */
    public void dropCredit() {
        this.credit = 0.0;
    }

    /** Measurement only: hands the machine an instant pile of blocks. */
    public void grant(int blocks) {
        this.credit = Math.max(this.credit, blocks);
    }
}
