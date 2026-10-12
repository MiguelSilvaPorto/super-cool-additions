package com.sccadd.requester;

/** The long target of a requester row (SCC itself keeps an int, capped at 9999). */
public interface BigRow {
    /** The real target; equals SCC's own int when no larger one was set. */
    long sccadd$bigAmount();

    void sccadd$setBigAmount(long value);

    /** Seconds between orders when set freely (0 = SCC's own preset). */
    long sccadd$intervalSeconds();

    void sccadd$setIntervalSeconds(long seconds);

    /** True while an order this row started is still being crafted. */
    boolean sccadd$jobRunning();
}
