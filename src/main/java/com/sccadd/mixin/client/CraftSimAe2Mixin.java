package com.sccadd.mixin.client;

import com.sccadd.ae2.client.Ae2PatternBook;
import de.scc.calculator.calc.CraftingPlan;
import de.scc.calculator.calc.ItemGraph;
import de.scc.calculator.calc.RecipeBook;
import de.scc.calculator.calc.SccRecipe;
import de.scc.calculator.calc.StackKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import de.scc.calculator.client.Ae2Book;
import de.scc.calculator.client.screen.CraftSim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When the addon knows the actual AE2 patterns of an item, the calculator should
 * not fall back to SCC's opaque single "AE2 can build it" step. Pretending the
 * item is not AE2-craftable here sends the planner down the normal recipe path,
 * where the detailed AE2 recipes injected into {@code CustomRecipeBook} are found.
 */
@Mixin(value = CraftSim.class, remap = false)
public abstract class CraftSimAe2Mixin {
    @Redirect(
            method = "candidatesFor",
            at = @At(
                    value = "INVOKE",
                    target = "Lde/scc/calculator/client/Ae2Book;canCraft(Lde/scc/calculator/calc/StackKey;)Z"
            ),
            require = 0
    )
    private static boolean sccadd$preferDetailedRecipes(StackKey key) {
        return !Ae2PatternBook.has(key) && Ae2Book.canCraft(key);
    }

    /**
     * The detailed pattern recipes are tried first (SCC leads), but SCC alone often cannot make what
     * a pattern's ingredients are - printed circuits, say - so an item AE2 can craft became "not
     * craftable". The plain "AE2 builds it" step is kept as the last candidate: when the detailed
     * recipes do not work out, AE2 does the job, exactly as SCC does without the addon.
     */
    @Inject(method = "candidatesFor", at = @At("RETURN"), cancellable = true, require = 0)
    private static void sccadd$keepAe2AsLastResort(StackKey item, long amount, CraftingPlan plan, RecipeBook book,
            ItemGraph graph, Map<StackKey, Long> stock, int depth, int[] budget, CallbackInfoReturnable<List<SccRecipe>> cir) {
        if (!Ae2PatternBook.has(item) || !Ae2Book.canCraft(item)) {
            return;
        }
        SccRecipe opaque = Ae2Book.recipeFor(item);
        List<SccRecipe> list = cir.getReturnValue();
        if (opaque == null || list == null || list.contains(opaque)) {
            return;
        }
        List<SccRecipe> more = new ArrayList<>(list.size() + 1);
        more.addAll(list);
        more.add(opaque);
        cir.setReturnValue(List.copyOf(more));
    }

    /**
     * SCC treats any single ingredient above 100,000 as "missing" before it even looks at
     * the stock, so a big order of a simple recipe (500,000 charcoal = 500,000 logs) reports
     * missing items although the storage holds billions. The cap only exists to stop runaway
     * recipe loops; a trillion still does that.
     */
    @ModifyConstant(method = "attemptOnce", constant = @Constant(longValue = 100000L), require = 0)
    private static long sccadd$ingredientCap(long original) {
        return 1_000_000_000_000L;
    }
}
