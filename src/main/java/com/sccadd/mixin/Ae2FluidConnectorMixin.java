package com.sccadd.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.sccadd.SccAddConfig;
import de.scc.calculator.compat.BridgeGuard;
import de.scc.calculator.compat.ae2.Ae2Hooks;
import de.scc.calculator.network.StorageConnectorBlockEntity;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a Storage Connector that faces an AE2 block also bridge <em>fluids</em>:
 * the ME network's fluids become visible and usable in the SCC network (and vice
 * versa), the same way items already work.
 */
@Mixin(value = StorageConnectorBlockEntity.class, remap = false)
public abstract class Ae2FluidConnectorMixin {
    @Unique
    private boolean sccadd$fluidAllowed(Fluid fluid) {
        return ((ConnectorAllowsInvoker) (Object) this).sccadd$connectorAllows(fluid);
    }

    @Unique
    private MEStorage sccadd$meStorage() {
        if (!SccAddConfig.ae2Fluids() || !Ae2Hooks.loaded() || BridgeGuard.busy()) {
            return null;
        }
        StorageConnectorBlockEntity self = (StorageConnectorBlockEntity) (Object) this;
        if (self instanceof com.sccadd.ae2.receiver.ReceiverMeConnector virtual) {
            return virtual.meStorage();
        }
        BlockPos target = self.ae2Target();
        if (target == null || self.getLevel() == null) {
            return null;
        }
        Object gridObject = Ae2Hooks.gridKey(self.getLevel(), target, self.ae2Side());
        if (!(gridObject instanceof IGrid grid)) {
            return null;
        }
        return grid.getStorageService().getInventory();
    }

    @Inject(method = "collectFluids", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$meFluidList(Map<Fluid, Long> out, CallbackInfo ci) {
        MEStorage storage = this.sccadd$meStorage();
        if (storage == null) {
            return;
        }
        KeyCounter counter = new KeyCounter();
        BridgeGuard.enter();
        try {
            storage.getAvailableStacks(counter);
        } catch (Throwable failed) {
            return;
        } finally {
            BridgeGuard.leave();
        }
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            if (entry.getKey() instanceof AEFluidKey fluid && entry.getLongValue() > 0L && this.sccadd$fluidAllowed(fluid.getFluid())) {
                out.merge(fluid.getFluid(), entry.getLongValue(), Long::sum);
            }
        }
        ci.cancel();
    }

    @Inject(method = "countFluid", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$meFluidCount(Fluid fluid, CallbackInfoReturnable<Long> cir) {
        MEStorage storage = this.sccadd$meStorage();
        if (storage == null || fluid == null || !this.sccadd$fluidAllowed(fluid)) {
            return;
        }
        AEFluidKey key = AEFluidKey.of(fluid);
        if (key == null) {
            return;
        }
        BridgeGuard.enter();
        try {
            cir.setReturnValue(storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()));
        } catch (Throwable failed) {
            // fall through to the normal container
        } finally {
            BridgeGuard.leave();
        }
    }

    @Inject(method = "drainFluid", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$meFluidDrain(Fluid fluid, int amount, boolean simulate, CallbackInfoReturnable<Integer> cir) {
        MEStorage storage = this.sccadd$meStorage();
        if (storage == null || fluid == null || amount <= 0 || !this.sccadd$fluidAllowed(fluid)) {
            return;
        }
        AEFluidKey key = AEFluidKey.of(fluid);
        if (key == null) {
            return;
        }
        BridgeGuard.enter();
        try {
            long drained = storage.extract(key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty());
            cir.setReturnValue((int) Math.min(Integer.MAX_VALUE, drained));
        } catch (Throwable failed) {
            // fall through to the normal container
        } finally {
            BridgeGuard.leave();
        }
    }

    @Inject(method = "fillFluid", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$meFluidFill(FluidStack stack, boolean simulate, CallbackInfoReturnable<Integer> cir) {
        MEStorage storage = this.sccadd$meStorage();
        if (storage == null || stack == null || stack.isEmpty() || !this.sccadd$fluidAllowed(stack.getFluid())) {
            return;
        }
        AEFluidKey key = AEFluidKey.of(stack);
        if (key == null) {
            return;
        }
        BridgeGuard.enter();
        try {
            long accepted = storage.insert(key, stack.getAmount(),
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty());
            cir.setReturnValue((int) Math.min(Integer.MAX_VALUE, accepted));
        } catch (Throwable failed) {
            // fall through to the normal container
        } finally {
            BridgeGuard.leave();
        }
    }
}
