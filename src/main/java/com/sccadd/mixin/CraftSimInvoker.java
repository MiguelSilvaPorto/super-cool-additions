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

    @Invoker("machineFor")
    static String sccadd$machineFor(de.scc.calculator.calc.SccRecipe recipe, RecipeBook book) {
        throw new AssertionError();
    }

    @Invoker("fluidsOf")
    static java.util.List<de.scc.calculator.network.CraftStep.Fluid> sccadd$fluidsOf(de.scc.calculator.calc.SccRecipe recipe) {
        throw new AssertionError();
    }

    @Invoker("returnsOf")
    static java.util.List<de.scc.calculator.network.CraftStep.Ing> sccadd$returnsOf(de.scc.calculator.calc.SccRecipe recipe,
            java.util.List<de.scc.calculator.network.CraftStep.Ing> inputs) {
        throw new AssertionError();
    }
}
