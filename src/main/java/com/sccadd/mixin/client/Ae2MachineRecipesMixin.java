package com.sccadd.mixin.client;

import com.sccadd.ae2.client.Ae2MachineRecipes;
import de.scc.calculator.calc.AnalysisManager;
import de.scc.calculator.calc.RecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds AE2's Inscriber and Charger recipes to SCC's recipe book (see {@link Ae2MachineRecipes}). */
@Mixin(value = AnalysisManager.class, remap = false)
public abstract class Ae2MachineRecipesMixin {
    @Inject(method = "book", at = @At("RETURN"), require = 0)
    private void sccadd$ae2MachineRecipes(CallbackInfoReturnable<RecipeBook> cir) {
        Ae2MachineRecipes.ensure(cir.getReturnValue());
    }
}
