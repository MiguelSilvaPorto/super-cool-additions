package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.processing.Catalysts;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.ProcessingCalculatorBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A press is reused, not a result. When the order is a press (iron block + press -> press), SCC saw
 * the press inside the Inscriber, took it for the finished result and pulled it out while the iron
 * block was still waiting - leaving the iron in a machine with no press, and the job stuck. A
 * reusable catalyst now stays in the machine for as long as anything else is still in there.
 */
@Mixin(value = ProcessingCalculatorBlockEntity.class, remap = false)
public abstract class ProcessorKeepCatalystMixin {
    @Inject(method = "mayPullItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$keepCatalyst(StackKey item, CallbackInfoReturnable<Boolean> cir) {
        if (!SccAddConfig.reuseCatalysts() || item == null || !Catalysts.is(item.display())) {
            return;
        }
        IItemHandler handler = ((ProcessingCalculatorBlockEntity) (Object) this).handler();
        if (handler == null) {
            return;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && !Catalysts.is(stack)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
