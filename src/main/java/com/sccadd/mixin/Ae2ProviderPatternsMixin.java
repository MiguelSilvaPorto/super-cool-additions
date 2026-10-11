package com.sccadd.mixin;

import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import com.sccadd.SccAddConfig;
import com.sccadd.ae2.SccAe2PatternStore;
import com.sccadd.ae2.SccAe2Recipe;
import de.scc.calculator.network.NetworkAntennaBlockEntity;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the recipe lines the client appends to the "what can SCC build"
 * answer and hands them to the per-antenna pattern store.
 */
@Mixin(targets = "de.scc.calculator.compat.ae2.Ae2Provider", remap = false)
public abstract class Ae2ProviderPatternsMixin {
    @Shadow
    @Final
    private NetworkAntennaBlockEntity be;

    @Shadow
    @Final
    private IManagedGridNode node;

    @Inject(method = "offered", at = @At("TAIL"), require = 0)
    private void sccadd$patternsFromOffer(String text, CallbackInfo ci) {
        List<SccAe2Recipe> parsed = SccAddConfig.sccPatternsInAe2() ? SccAe2Recipe.parse(text) : List.of();
        if (parsed.equals(SccAe2PatternStore.recipes(this.be))) {
            return;
        }
        SccAe2PatternStore.setRecipes(this.be, parsed);
        try {
            ICraftingProvider.requestUpdate(this.node);
        } catch (Throwable failed) {
            // The node may not be created yet; the next refresh picks it up.
        }
    }
}
