package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.processing.Catalysts;
import de.scc.calculator.network.NetworkScanner;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.ProcessingCalculatorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shows the reusable catalysts (presses) inside a calculator's machine as network stock. */
@Mixin(value = NetworkScanner.class, remap = false)
public abstract class ProcessorCatalystScannerMixin {
    @Inject(method = "addTyped", at = @At("HEAD"), require = 0)
    private static void sccadd$machineCatalysts(NetworkView view, BlockEntity be, CallbackInfo ci) {
        if (be instanceof ProcessingCalculatorBlockEntity calculator && SccAddConfig.reuseCatalysts()
                && calculator.getLevel() != null && calculator.hasInventory()) {
            ((NetworkViewAdd) (Object) view).sccadd$add(Catalysts.connector(calculator));
        }
    }
}
