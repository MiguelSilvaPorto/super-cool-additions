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
    @Inject(method = "pushOnce", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$swapCatalysts(NetworkView view, ProcessingCalculatorBlockEntity machine, CraftStep step,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!SccAddConfig.reuseCatalysts() || machine.getLevel() == null) {
            return;
        }
        IItemHandler handler = machine.handler();
        if (handler == null) {
            return;
        }
        Set<StackKey> related = new HashSet<>();
        Set<StackKey> needed = new HashSet<>();
        boolean usesCatalyst = false;
        boolean missing = false;
        related.add(step.output());
        for (CraftStep.Ing input : step.inputs()) {
            related.add(input.item());
            if (!input.consumed()) {
                usesCatalyst = true;
                needed.add(input.item());
                missing |= !machine.hasInMachine(input.item());
            }
        }
        boolean holdsCatalyst = false;
        boolean foreign = false;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (Catalysts.is(stack)) {
                holdsCatalyst = true;
            } else if (!related.contains(StackKey.of(stack))) {
                foreign = true;
            }
        }
        if (!usesCatalyst && !holdsCatalyst) {
            return;
        }
        if (foreign) {
            // Another recipe's items are in the machine (its inputs waiting for their press, or its result not
            // yet taken out). Mixing two recipes in one Inscriber fills the slots with the wrong things and
            // neither finishes, so this step waits until the machine is clear.
            cir.setReturnValue(false);
            return;
        }
        if (!missing) {
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
