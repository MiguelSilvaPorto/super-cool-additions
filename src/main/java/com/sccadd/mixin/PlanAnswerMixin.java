package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.planner.PlannerPick;
import de.scc.calculator.network.RequesterPlanPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every planning answer passes through here; remembering who answered lets
 * {@link PlannerPick} keep sending requests to clients that actually respond.
 */
@Mixin(value = RequesterPlanPayload.class, remap = false)
public abstract class PlanAnswerMixin {
    @Inject(method = "handle", at = @At("HEAD"), require = 0)
    private static void sccadd$noteAnswer(RequesterPlanPayload payload, IPayloadContext context, CallbackInfo ci) {
        if (!SccAddConfig.smartPlanner()) {
            return;
        }
        if (context.player() instanceof ServerPlayer player && player.getServer() != null) {
            PlannerPick.answered(player, player.getServer().getTickCount());
        }
    }
}
