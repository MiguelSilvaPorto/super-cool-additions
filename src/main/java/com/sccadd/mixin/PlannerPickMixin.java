package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.planner.PlannerPick;
import de.scc.calculator.network.FuelOrders;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces SCC's "first player in the list" planner choice (see {@link PlannerPick}).
 */
@Mixin(value = FuelOrders.class, remap = false)
public abstract class PlannerPickMixin {
    @Inject(method = "pickPlanner", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sccadd$smartPlanner(MinecraftServer server, CallbackInfoReturnable<ServerPlayer> cir) {
        if (!SccAddConfig.smartPlanner()) {
            return;
        }
        ServerPlayer picked = PlannerPick.pick(server);
        if (picked != null) {
            cir.setReturnValue(picked);
        }
    }
}
