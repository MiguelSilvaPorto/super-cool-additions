package com.sccadd.mixin.client;

import com.sccadd.ae2.client.Ae2PatternBook;
import de.scc.calculator.calc.SccRecipe;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.client.CustomRecipeBook;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the detailed AE2 pattern recipes visible wherever SCC asks for the
 * recipes of an item (calculator tree, recipe choices, ordering).
 */
@Mixin(value = CustomRecipeBook.class, remap = false)
public abstract class CustomRecipeBookMixin {
    @Inject(method = "forOutput", at = @At("RETURN"), cancellable = true, require = 0)
    private static void sccadd$appendAe2Patterns(StackKey item, CallbackInfoReturnable<List<SccRecipe>> cir) {
        List<SccRecipe> extra = Ae2PatternBook.recipesFor(item);
        if (extra.isEmpty()) {
            return;
        }
        List<SccRecipe> base = cir.getReturnValue();
        List<SccRecipe> merged = new ArrayList<>(base.size() + extra.size());
        merged.addAll(base);
        merged.addAll(extra);
        cir.setReturnValue(List.copyOf(merged));
    }
}
