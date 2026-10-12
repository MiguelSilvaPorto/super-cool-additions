package com.sccadd.mixin.client;

import com.sccadd.SccAddConfig;
import com.sccadd.analysis.FingerprintExtra;
import de.scc.calculator.calc.AnalysisManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the analysis cache resilient.
 *
 * <p>SCC splits its fingerprint in a hard part (cache thrown away when it differs)
 * and a soft part (cache used immediately, refreshed in the background). The hard
 * part contains the versions of every mod and almost every config field, so any
 * pack update or settings tweak costs a full re-analysis - on ATM10 that is
 * 8-12 minutes without a usable system.</p>
 *
 * <p>This mixin moves that information to the soft part: the cache is always
 * loaded, and a change simply schedules the background refresh SCC already has.
 * The cache file format is still protected by the {@code #SCC62} header, so
 * caches from older SCC logic are still rejected.</p>
 */
@Mixin(value = AnalysisManager.class, remap = false)
public abstract class AnalysisFingerprintMixin {
    @Inject(method = "hardPrint", at = @At("RETURN"), cancellable = true, require = 0)
    private static void sccadd$resilientHardPrint(CallbackInfoReturnable<String> cir) {
        if (SccAddConfig.resilientAnalysisCache()) {
            cir.setReturnValue("sccadd1");
        }
    }

    @Inject(method = "softPrint", at = @At("RETURN"), cancellable = true, require = 0)
    private static void sccadd$extendedSoftPrint(CallbackInfoReturnable<String> cir) {
        if (SccAddConfig.resilientAnalysisCache()) {
            cir.setReturnValue(cir.getReturnValue() + "-" + FingerprintExtra.signature() + "-" + FingerprintExtra.recipeShape());
        }
    }
}
