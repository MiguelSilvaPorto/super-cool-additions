package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.ae2.power.FiberPowers;
import de.scc.calculator.compat.ae2.Ae2Hooks;
import de.scc.calculator.network.NetworkBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops the AE2 power node of an SCC network block when the block is removed,
 * so a stale node does not keep the fiber's link (and the power flow) alive.
 */
@Mixin(value = NetworkBlockEntity.class, remap = false)
public abstract class Ae2FiberRemoveMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"), require = 0)
    private void sccadd$fiberRemoved(CallbackInfo ci) {
        if (SccAddConfig.ae2FiberPower() && Ae2Hooks.loaded()) {
            FiberPowers.removed((NetworkBlockEntity) (Object) this);
        }
    }
}
