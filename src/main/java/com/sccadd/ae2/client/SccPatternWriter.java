package com.sccadd.ae2.client;

import de.scc.calculator.calc.AnalysisManager;
import de.scc.calculator.calc.ItemGraph;
import de.scc.calculator.calc.RecipeBook;
import de.scc.calculator.calc.SccRecipe;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.client.screen.CraftSim;
import de.scc.calculator.network.CraftStep;
import de.scc.calculator.SccLog;
import com.sccadd.SccAddConfig;
import com.sccadd.mixin.CraftSimInvoker;
import com.sccadd.mixin.RequesterPlannerInvoker;
import java.util.Map;
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

    /** Stock text of the running offer computation. */
    private static final ThreadLocal<String> STOCK = new ThreadLocal<>();

    private SccPatternWriter() {
    }

    public static void setTarget(BlockPos pos) {
        TARGET.set(pos);
    }

    public static void setStock(String stock) {
        STOCK.set(stock);
    }

    public static void clearTarget() {
        TARGET.remove();
        STOCK.remove();
    }

    public static BlockPos target() {
        return TARGET.get();
    }

    /**
     * Keeps only the items SCC can really make with the network's current stock,
     * using the exact same reachability filter as the SCC terminal (via
     * {@link CraftSimInvoker}). Everything else is dropped from the offer, so AE2
     * never lists items that cannot be built right now.
     */
    public static String filterOffer(String offerText) {
        if (!SccAddConfig.offerRequiresStock() || offerText == null || offerText.isEmpty()) {
            return offerText;
        }
        // The SCC terminal's "Only with ingredients" button decides: off = show every recipe.
        if (!de.scc.calculator.SccConfig.get().onlyAvailableCraftable) {
            return offerText;
        }
        RecipeBook book = AnalysisManager.get().book();
        ItemGraph graph = AnalysisManager.get().graph();
        if (book == null || graph == null) {
            return offerText;
        }
        String stockText = STOCK.get();
        Map<StackKey, Long> stock;
        try {
            // An empty stock text means the network has nothing - then nothing is
            // possible, and the offer has to become empty too.
            stock = stockText == null || stockText.isBlank()
                    ? Map.of()
                    : RequesterPlannerInvoker.sccadd$parseStock(stockText);
        } catch (Throwable failed) {
            return offerText;
        }
        if (stock == null) {
            stock = Map.of();
        }
        StringBuilder out = new StringBuilder(offerText.length());
        int kept = 0;
        int total = 0;
        for (String line : offerText.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            StackKey key = StackKey.decode(line.trim());
            if (key == null) {
                out.append(line).append('\n');
                continue;
            }
            total++;
            try {
                if (CraftSimInvoker.sccadd$reach(key, book, graph, stock) <= 5) {
                    out.append(line).append('\n');
                    kept++;
                }
            } catch (Throwable failed) {
                out.append(line).append('\n');
                kept++;
            }
        }
        SccLog.info("sccadd", "offer filtered by stock: " + kept + " of " + total + " item(s) are buildable now");
        return out.toString();
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

    private static boolean usable(SccRecipe recipe, RecipeBook book) {
        if (recipe == null || CraftStep.isExternal(recipe.typeId()) || recipe.chancy()) {
            return false;
        }
        if (recipe.inputs().isEmpty() && recipe.fluids().isEmpty()) {
            return false;
        }
        if (recipe.inputs().size() > MAX_INPUTS) {
            return false;
        }
        try {
            return CraftSim.canRun(recipe, book);
        } catch (Throwable failed) {
            return false;
        }
    }

    /** SCC's own choice first (the analysis plan), so AE2 shows what SCC will really do. */
    private static SccRecipe pick(StackKey key, RecipeBook book) {
        var plan = AnalysisManager.get().plan();
        SccRecipe chosen = plan == null ? null : plan.bestRecipe(key);
        if (usable(chosen, book)) {
            return chosen;
        }
        for (SccRecipe recipe : book.recipesFor(key)) {
            if (recipe == null || CraftStep.isExternal(recipe.typeId())) {
                continue;
            }
            if (recipe.chancy()) {
                continue;
            }
            if (recipe.inputs().isEmpty() && recipe.fluids().isEmpty()) {
                continue;
            }
            if (recipe.inputs().size() > MAX_INPUTS) {
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
        for (SccRecipe.Fluid fluid : recipe.fluids()) {
            if (fluid.optional() || fluid.amount() <= 0 || fluid.id() == null || fluid.id().isBlank()) {
                continue;
            }
            if (!first) {
                sb.append(';');
            }
            first = false;
            sb.append('F').append(Math.max(1, fluid.amount())).append(':').append(fluid.id());
        }
        return first ? null : sb.append('\n').toString();
    }
}
