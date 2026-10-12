package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.ae2.receiver.ReceiverBridges;
import com.sccadd.ae2.receiver.ReceiverMeConnector;
import de.scc.calculator.compat.ae2.Ae2Hooks;
import de.scc.calculator.network.NetworkScanner;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.WirelessReceiverBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Joins a wireless receiver's AE2 grid to the SCC network as one more storage, so
 * what the ME holds shows up as SCC stock.
 */
@Mixin(value = NetworkScanner.class, remap = false)
public abstract class Ae2NetworkScannerMixin {
    @Inject(method = "addTyped", at = @At("HEAD"), require = 0)
    private static void sccadd$receiverMe(NetworkView view, BlockEntity be, CallbackInfo ci) {
        if (be instanceof WirelessReceiverBlockEntity receiver
                && SccAddConfig.bridgeOnReceiver() && SccAddConfig.receiverReadsMe() && Ae2Hooks.loaded()) {
            ReceiverMeConnector connector = ReceiverBridges.connector(receiver);
            if (connector != null) {
                ((NetworkViewAdd) (Object) view).sccadd$add(connector);
            }
        }
    }
}
