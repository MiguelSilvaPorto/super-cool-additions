package com.sccadd.breaking;

/**
 * What the addon needs to see inside a Breaking Calculator. The calculator mixin implements this,
 * so {@code /sccadd breaking} can report the batch state without touching SCC's fields.
 */
public interface BreakingMetrics {
    /** Blocks the machine has earned and not delivered yet. */
    double sccadd$credit();

    /** Whole blocks of that credit. */
    int sccadd$earned();

    /** Items the round-robin is really cycling through. */
    int sccadd$activeKeys();

    /** Items waiting in the hidden buffer. */
    int sccadd$bufferTotal();

    /** Items the hidden buffer can hold at this tier. */
    int sccadd$bufferCapacity();

    /** The last work call delivered a batch. */
    boolean sccadd$batching();

    /** Measurement only: makes the next call worth {@code blocks} blocks. */
    void sccadd$stress(int blocks);
}
