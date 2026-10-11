package com.sccadd.mixin.client;

import com.sccadd.SccAddConfig;
import com.sccadd.ae2.client.SccPatternWriter;
import de.scc.calculator.client.RequesterPlanner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hooks the moment the offer answer is built: the plain item list gets recipe
 * lines appended so AE2 can see the actual ingredients SCC would need.
 */
@Mixin(value = RequesterPlanner.class, remap = false)
public abstract class Ae2OfferPatternsMixin {
    @Redirect(
            method = "offer",
            at = @At(value = "INVOKE", target = "Ljava/lang/StringBuilder;toString()Ljava/lang/String;"),
            require = 0
    )
    private static String sccadd$appendPatterns(StringBuilder sb) {
        String base = sb.toString();
        if (!SccAddConfig.sccPatternsInAe2()) {
            return base;
        }
        try {
            return SccPatternWriter.append(base);
        } catch (Throwable failed) {
            return base;
        }
    }
}
