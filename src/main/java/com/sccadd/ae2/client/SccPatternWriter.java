package com.sccadd.ae2.client;

import de.scc.calculator.calc.AnalysisManager;
import de.scc.calculator.calc.RecipeBook;
import de.scc.calculator.calc.SccRecipe;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.client.screen.CraftSim;
import de.scc.calculator.network.CraftStep;
import net.minecraft.core.BlockPos;

/**
 * Turns SCC's offer list into recipe lines for AE2. No size ceiling: the text is
 * split into chunks by the caller and sent in small packets, so a big pack is
 * fine. The only guards are for recipe <em>fidelity</em> - a recipe with more
 * ingredients or options than these is skipped rather than sent incomplete.
 */
public final class SccPatternWriter {
    public static final int MAX_INPUTS = 27;
    public static final int MAX_OPTIONS = 128;

    /** Target of the running offer computation (the bridge block that asked). */
    private static final ThreadLocal<BlockPos> TARGET = new ThreadLocal<>();

    private SccPatternWriter() {
    }

    public static void setTarget(BlockPos pos) {
        TARGET.set(pos);
    }

    public static void clearTarget() {
        TARGET.remove();
    }

    public static BlockPos target() {
        return TARGET.get();
    }

    /** The recipe lines ({@code P\t...}) for every item in the offer text. */
    public static String patternsFor(String offerText) {
        RecipeBook book = AnalysisManager.get().book();
        if (book == null || offerText == null || offerText.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(8_192);
        for (String line : offerText.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            StackKey key = StackKey.decode(line.trim());
            if (key == null) {
                continue;
            }
            SccRecipe recipe = pick(key, book);
            if (recipe == null) {
                continue;
            }
            String encoded = encode(key, recipe);
            if (encoded != null) {
                out.append(encoded);
            }
        }
        return out.toString();
    }

    private static SccRecipe pick(StackKey key, RecipeBook book) {
        for (SccRecipe recipe : book.recipesFor(key)) {
            if (recipe == null || CraftStep.isExternal(recipe.typeId())) {
                continue;
            }
            if (!recipe.fluids().isEmpty() || recipe.chancy()) {
                continue;
            }
            if (recipe.inputs().isEmpty() || recipe.inputs().size() > MAX_INPUTS) {
                continue;
            }
            try {
                if (!CraftSim.canRun(recipe, book)) {
                    continue;
                }
            } catch (Throwable failed) {
                continue;
            }
            return recipe;
        }
        return null;
    }

    private static String encode(StackKey output, SccRecipe recipe) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("P\t").append(output.encode()).append('\t').append(Math.max(1, recipe.outputCount())).append('\t');
        boolean first = true;
        for (SccRecipe.Input input : recipe.inputs()) {
            if (input.options().isEmpty()) {
                return null;
            }
            if (!first) {
                sb.append(';');
            }
            first = false;
            sb.append(Math.max(1, input.count()));
            int options = 0;
            for (StackKey option : input.options()) {
                sb.append(',').append(option.encode());
                if (++options >= MAX_OPTIONS) {
                    break;
                }
            }
        }
        return first ? null : sb.append('\n').toString();
    }
}
