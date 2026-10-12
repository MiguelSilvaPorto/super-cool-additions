package com.sccadd.mixin;

import de.scc.calculator.calc.StackKey;
import de.scc.calculator.client.RequesterPlanner;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuses SCC's own stock parser (the one the offer computation already uses). */
@Mixin(value = RequesterPlanner.class, remap = false)
public interface RequesterPlannerInvoker {
    @Invoker("parseStock")
    static Map<StackKey, Long> sccadd$parseStock(String text) {
        throw new AssertionError();
    }
}
