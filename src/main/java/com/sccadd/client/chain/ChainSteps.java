package com.sccadd.client.chain;

import com.sccadd.chain.ChainData;
import com.sccadd.mixin.CraftSimInvoker;
import de.scc.calculator.calc.AnalysisManager;
import de.scc.calculator.calc.RecipeBook;
import de.scc.calculator.calc.SccRecipe;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.CraftStep;
import java.util.ArrayList;
import java.util.List;

/** Turns a recipe the player picked into the one-craft step a chain box keeps. */
public final class ChainSteps {
    private ChainSteps() {
    }

    public static List<SccRecipe> recipesFor(StackKey item) {
        RecipeBook book = AnalysisManager.get().book();
        return book == null || item == null ? List.of() : book.recipesFor(item);
    }

    /** Fills the recipe part of the box; the item and numbers are left alone. */
    public static void apply(ChainData.Node node, SccRecipe recipe) {
        RecipeBook book = AnalysisManager.get().book();
        List<CraftStep.Ing> inputs = new ArrayList<>();
        node.alts = new ArrayList<>();
        for (SccRecipe.Input input : recipe.inputs()) {
            if (input.options().isEmpty()) {
                continue;
            }
            inputs.add(new CraftStep.Ing(input.options().get(0), Math.max(1, input.count()), input.consumed()));
            StringBuilder options = new StringBuilder();
            if (input.options().size() > 1) {
                for (StackKey option : input.options()) {
                    if (options.length() > 0) {
                        options.append('|');
                    }
                    options.append(option.encode());
                }
            }
            node.alts.add(options.toString());
        }
        CraftStep step = new CraftStep(recipe.id(), recipe.typeId(), recipe.output(), recipe.outputCount(), 1L, inputs,
                CraftSimInvoker.sccadd$machineFor(recipe, book), CraftSimInvoker.sccadd$fluidsOf(recipe), List.of(),
                recipe.chance(), 0L, CraftSimInvoker.sccadd$returnsOf(recipe, inputs));
        node.step = step.encode();
    }

    public static CraftStep stepOf(ChainData.Node node) {
        return node.step.isEmpty() ? null : CraftStep.decode(node.step);
    }
}
