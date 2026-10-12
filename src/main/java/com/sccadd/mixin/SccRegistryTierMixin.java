package com.sccadd.mixin;

import com.sccadd.village.VillageTiers;
import de.scc.calculator.network.SccRegistry;
import de.scc.calculator.network.SccTier;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Teaches SCC's registry about the village chambers: which tier one is and which block a tier is. With that,
 * the SCC upgrade items (step stones and the jump ones) work on a chamber by right-click, and the Calculator
 * Controller's network-wide upgrade plans and upgrades them along with the rest of the network.
 */
@Mixin(value = SccRegistry.class, remap = false)
public abstract class SccRegistryTierMixin {
    @Inject(method = "tierOf", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sccadd$tierOf(Block block, CallbackInfoReturnable<SccTier> cir) {
        SccTier tier = VillageTiers.tierOf(block);
        if (tier != null) {
            cir.setReturnValue(tier);
        }
    }

    @Inject(method = "sharedTierOf", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sccadd$sharedTierOf(Block block, CallbackInfoReturnable<SccTier> cir) {
        SccTier tier = VillageTiers.tierOf(block);
        if (tier != null) {
            cir.setReturnValue(tier);
        }
    }

    @Inject(method = "sameFamily", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sccadd$sameFamily(Block block, SccTier target, CallbackInfoReturnable<Block> cir) {
        Block next = VillageTiers.family(block, target);
        if (next != null) {
            cir.setReturnValue(next);
        }
    }
}
