package com.sccadd.mixin;

import de.scc.calculator.calc.ItemGraph;
import de.scc.calculator.calc.RecipeBook;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.client.screen.CraftSim;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reuses SCC's own reachability filter: exactly what the terminal uses to show
 * what can be made with the current stock (0..5 = reachable, 6/7 = not).
 */
@Mixin(value = CraftSim.class, remap = false)
public interface CraftSimInvoker {
    @Invoker("reach")
    static int sccadd$reach(StackKey item, RecipeBook book, @Nullable ItemGraph graph, @Nullable Map<StackKey, Long> stock) {
        throw new AssertionError();
    }
}
