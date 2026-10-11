package com.sccadd.mixin.client;

import com.sccadd.ae2.client.Ae2PatternBook;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.client.Ae2Book;
import de.scc.calculator.client.screen.CraftSim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
}
