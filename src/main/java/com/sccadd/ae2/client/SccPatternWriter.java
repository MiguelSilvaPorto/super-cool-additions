package com.sccadd.ae2.client;

import de.scc.calculator.calc.AnalysisManager;
import de.scc.calculator.calc.RecipeBook;
import de.scc.calculator.calc.SccRecipe;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.client.screen.CraftSim;
import de.scc.calculator.network.CraftStep;

/**
 * Appends the recipe lines for every offered item to SCC's "what can SCC build"
 * answer. The server turns them into real AE2 patterns (see
 * {@code com.sccadd.ae2.SccAe2Patterns}); SCC itself ignores lines it cannot
 * decode, so old servers stay compatible.
 */
public final class SccPatternWriter {
    /**
     * No arbitrary cap: whatever SCC's own offer list says it can build is sent
     * with a real recipe. SCC already caps its offer (8000 items); the wire codec
     * handles up to 16 MB with automatic compression, so these numbers are just
     * an emergency stop far above any real pack.
     */
    public static final int MAX_PATTERNS = 20_000;
    public static final int MAX_CHARS = 12_000_000;
    public static final int MAX_INPUTS = 27;
    public static final int MAX_OPTIONS = 128;

    private SccPatternWriter() {
    }

    public static String append(String offerText) {
        RecipeBook book = AnalysisManager.get().book();
        if (book == null) {
            return offerText;
        }
        StringBuilder out = new StringBuilder(offerText.length() + 8_192);
        out.append(offerText);
        int patterns = 0;
        for (String line : offerText.split("\n")) {
            if (patterns >= MAX_PATTERNS || out.length() > MAX_CHARS) {
                break;
            }
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
                patterns++;
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
