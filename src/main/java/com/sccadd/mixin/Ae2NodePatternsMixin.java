package com.sccadd.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.sccadd.ae2.SccAe2PatternStore;
import com.sccadd.ae2.SccAe2Patterns;
import de.scc.calculator.network.NetworkAntennaBlockEntity;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The antenna's grid node: instead of the (empty) pattern list SCC ships, it now
 * offers the SCC recipes as real AE2 patterns and accepts the ingredient push
 * from AE2 CPUs.
 */
@Mixin(targets = "de.scc.calculator.compat.ae2.Ae2Bridge$Node", remap = false)
public abstract class Ae2NodePatternsMixin {
    @Shadow
    @Final
    private NetworkAntennaBlockEntity be;

    @Inject(method = "getAvailablePatterns", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$availablePatterns(CallbackInfoReturnable<List<IPatternDetails>> cir) {
        cir.setReturnValue(SccAe2PatternStore.patterns(this.be));
    }

    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$pushPattern(IPatternDetails pattern, KeyCounter[] inputs, CallbackInfoReturnable<Boolean> cir) {
        Boolean result = SccAe2Patterns.push(this.be, SccAe2PatternStore.recipes(this.be), pattern, inputs);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
