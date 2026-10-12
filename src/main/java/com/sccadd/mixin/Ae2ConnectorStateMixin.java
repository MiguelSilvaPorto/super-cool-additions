package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import de.scc.calculator.SccLog;
import de.scc.calculator.network.BulkItemStore;
import de.scc.calculator.network.StorageConnectorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnosis aid: when a Storage Connector cannot find an ME storage at its
 * target, say what block it actually faces - usually it is not an AE2 block.
 */
@Mixin(value = StorageConnectorBlockEntity.class, remap = false)
public abstract class Ae2ConnectorStateMixin {
    @Unique
    private static long sccadd$nextLog;

    @Unique
    private static long sccadd$nextHandlerLog;

    @Inject(method = "handler", at = @At("RETURN"), require = 0)
    private void sccadd$logHandler(CallbackInfoReturnable<IItemHandler> cir) {
        if (!SccAddConfig.ae2DebugOffer() || cir.getReturnValue() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < sccadd$nextHandlerLog) {
            return;
        }
        sccadd$nextHandlerLog = now + 10_000L;
        try {
            IItemHandler handler = cir.getReturnValue();
            String kind = handler.getClass().getSimpleName();
            if (handler instanceof BulkItemStore store) {
                int kinds = 0;
                long total = 0L;
                for (ItemStack stack : store.entries()) {
                    if (!stack.isEmpty()) {
                        kinds++;
                        total += stack.getCount();
                    }
                }
                SccLog.info("sccadd", "storage connector at "
                        + ((StorageConnectorBlockEntity) (Object) this).getBlockPos().toShortString()
                        + ": ME handler (" + kind + ") reads " + kinds + " kind(s), " + total + " item(s)");
            } else {
                SccLog.info("sccadd", "storage connector at "
                        + ((StorageConnectorBlockEntity) (Object) this).getBlockPos().toShortString()
                        + ": handler is " + kind + " (not an ME storage)");
            }
        } catch (Throwable failed) {
            // diagnosis only
        }
    }

    @Inject(method = "meHandler", at = @At("RETURN"), require = 0)
    private void sccadd$logMeState(ServerLevel server, BlockPos target, Direction side,
                                   CallbackInfoReturnable<IItemHandler> cir) {
        if (!SccAddConfig.ae2DebugOffer() || cir.getReturnValue() != null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < sccadd$nextLog) {
            return;
        }
        sccadd$nextLog = now + 10_000L;
        try {
            BlockState state = server.getBlockState(target);
            SccLog.warn("sccadd", "storage connector at "
                    + ((StorageConnectorBlockEntity) (Object) this).getBlockPos().toShortString()
                    + " faces " + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                    + " and found no ME storage there - the target must be an AE2 block");
        } catch (Throwable failed) {
            // diagnosis only
        }
    }
}
