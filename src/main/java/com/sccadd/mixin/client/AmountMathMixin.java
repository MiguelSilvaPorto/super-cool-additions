package com.sccadd.mixin.client;

import com.sccadd.requester.BigMath;
import de.scc.calculator.client.screen.AmountMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SCC's amount fields accept decimals and the suffixes k, m, g and t as well (see {@link BigMath}). */
@Mixin(value = AmountMath.class, remap = false)
public abstract class AmountMathMixin {
    @Inject(method = "allowed", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sccadd$moreChars(char c, CallbackInfoReturnable<Boolean> cir) {
        if (BigMath.allowedChar(c)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "eval", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sccadd$bigEval(String s, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(BigMath.eval(s));
    }
}
