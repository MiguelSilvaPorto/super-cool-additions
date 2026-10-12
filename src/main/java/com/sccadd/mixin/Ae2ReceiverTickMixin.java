package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.ae2.receiver.ReceiverBridges;
import de.scc.calculator.compat.ae2.Ae2Hooks;
import de.scc.calculator.network.WirelessReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ticks the AE2 bridge of the wireless receiver (item mount, recipe offer,
 * delivery of finished jobs).
 */
@Mixin(value = WirelessReceiverBlockEntity.class, remap = false)
public abstract class Ae2ReceiverTickMixin {
    @Inject(method = "serverTick", at = @At("TAIL"), require = 0)
    private static void sccadd$receiverBridgeTick(Level level, BlockPos pos, BlockState state,
                                                  WirelessReceiverBlockEntity be, CallbackInfo ci) {
        if (SccAddConfig.bridgeOnReceiver() && Ae2Hooks.loaded()) {
            ReceiverBridges.tick(be);
        }
    }
}
