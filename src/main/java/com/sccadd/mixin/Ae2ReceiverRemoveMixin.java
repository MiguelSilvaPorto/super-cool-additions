package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.ae2.receiver.ReceiverBridges;
import de.scc.calculator.compat.ae2.Ae2Hooks;
import de.scc.calculator.network.NetworkBlockEntity;
import de.scc.calculator.network.WirelessReceiverBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Destroys the receiver's AE2 node when the block is removed, so the bridge
 * does not leak into the grid.
 */
@Mixin(value = NetworkBlockEntity.class, remap = false)
public abstract class Ae2ReceiverRemoveMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"), require = 0)
    private void sccadd$receiverBridgeRemoved(CallbackInfo ci) {
        if (((Object) this) instanceof WirelessReceiverBlockEntity receiver
                && SccAddConfig.bridgeOnReceiver()
                && Ae2Hooks.loaded()) {
            ReceiverBridges.removed(receiver);
        }
    }
}
