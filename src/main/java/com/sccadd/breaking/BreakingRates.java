package com.sccadd.breaking;

import com.sccadd.SccAddConfig;
import de.scc.calculator.network.BreakingCalculatorBlockEntity;
import de.scc.calculator.network.BreakingGrade;
import de.scc.calculator.network.BreakingOverdrive;

/**
 * How many blocks a Breaking Calculator really earns per tick: the tier's own ratio, or the
 * Overdrive rate while it is running on Chronon, times the {@code speedup} config.
 *
 * <p>This is the clock the batch mode banks against, so waiting for a bigger batch can never
 * change the machine's real speed - only when the work happens.</p>
 */
public final class BreakingRates {
    private BreakingRates() {
    }

    public static double perTick(BreakingCalculatorBlockEntity be) {
        BreakingGrade grade = be.grade();
        int step = be.overdriveStep();
        double blocksPerTick;
        if (step > 0) {
            // The Overdrive rate is blocks per second.
            blocksPerTick = BreakingOverdrive.rate(grade, step) / 20.0;
        } else {
            blocksPerTick = (double) grade.configuredBlocks() / Math.max(1, grade.configuredTicks());
        }
        return blocksPerTick * SccAddConfig.speedup();
    }
}
