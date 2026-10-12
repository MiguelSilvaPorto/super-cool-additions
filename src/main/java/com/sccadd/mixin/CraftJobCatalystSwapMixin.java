package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.processing.Catalysts;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.CraftJob;
import de.scc.calculator.network.CraftStep;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.ProcessingCalculatorBlockEntity;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A machine that holds the wrong press for this recipe gets it back first: the old press goes back
 * to the network so the right one fits. Without this, presses piled up in the machine and the
 * recipe never found a free slot.
 */
@Mixin(value = CraftJob.class, remap = false)
public abstract class CraftJobCatalystSwapMixin {
    @Inject(method = "pushOnce", at = @At("HEAD"), require = 0)
    private void sccadd$swapCatalysts(NetworkView view, ProcessingCalculatorBlockEntity machine, CraftStep step,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!SccAddConfig.reuseCatalysts() || machine.getLevel() == null) {
            return;
        }
        Set<StackKey> needed = new HashSet<>();
        boolean missing = false;
        for (CraftStep.Ing input : step.inputs()) {
            if (!input.consumed()) {
                needed.add(input.item());
                missing |= !machine.hasInMachine(input.item());
            }
        }
        if (!missing) {
            return;
        }
        IItemHandler handler = machine.handler();
        if (handler == null) {
            return;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !Catalysts.is(stack)) {
                continue;
            }
            StackKey key = StackKey.of(stack);
            if (needed.contains(key)) {
                continue;
            }
            ItemStack back = machine.takeBack(key, stack.getCount());
            if (!back.isEmpty()) {
                view.giveBack(back, machine.getLevel(), machine.getBlockPos());
            }
        }
    }
}
