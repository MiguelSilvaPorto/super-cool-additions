package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.processing.Catalysts;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.ProcessingCalculatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When the recipes are done, the presses go back to the system: a machine that holds only presses,
 * has no job and has been idle for a few seconds gives them back. The wait keeps a press in place
 * between two recipes of the same order, so it is not pulled out just to be pulled in again.
 */
@Mixin(value = ProcessingCalculatorBlockEntity.class, remap = false)
public abstract class ProcessorReleaseCatalystMixin {
    @Inject(method = "serverTick", at = @At("TAIL"), require = 0)
    private static void sccadd$releaseIdleCatalysts(Level level, BlockPos pos, BlockState state,
                                                    ProcessingCalculatorBlockEntity machine, CallbackInfo ci) {
        if (!SccAddConfig.reuseCatalysts() || level.isClientSide || !machine.hasInventory()) {
            return;
        }
        IItemHandler handler = machine.handler();
        if (handler == null) {
            return;
        }
        boolean holdsPress = false;
        boolean other = false;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (Catalysts.is(stack)) {
                holdsPress = true;
            } else {
                other = true;
            }
        }
        if (!holdsPress || other || machine.reservedByOther(0L) || machine.awaitingResult() || machine.stillWorking()) {
            Catalysts.idle(machine, false);
            return;
        }
        if (Catalysts.idle(machine, true) < Catalysts.RELEASE_AFTER_TICKS) {
            return;
        }
        NetworkView view = machine.network();
        if (view == null || !view.online()) {
            return;
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !Catalysts.is(stack)) {
                continue;
            }
            ItemStack back = machine.takeBack(StackKey.of(stack), stack.getCount());
            if (!back.isEmpty()) {
                view.giveBack(back, level, pos);
            }
        }
    }
}
