package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.ae2.receiver.ReceiverBridges;
import com.sccadd.planner.PlannerPick;
import de.scc.calculator.network.FuelOrders;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.RequesterPlanPayload;
import de.scc.calculator.network.SccParts;
import de.scc.calculator.network.WirelessReceiverBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Three jobs:
 * <ul>
 *   <li>remember who answered a planning request (for the smarter planner pick),</li>
 *   <li>route the "what can SCC build" answer (row -9) to a wireless receiver bridge,</li>
 *   <li>route a job plan answer (row -8) to a wireless receiver bridge, so the SCC
 *       job actually starts after AE2 pushed the ingredients.</li>
 * </ul>
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

    @Inject(method = "handle", at = @At("TAIL"), require = 0)
    private static void sccadd$routeToReceiver(RequesterPlanPayload payload, IPayloadContext context, CallbackInfo ci) {
        if (!SccAddConfig.bridgeOnReceiver()) {
            return;
        }
        int row = FuelOrders.rowOf(payload.row());
        if (row != FuelOrders.AE2_OFFER_ROW && row != FuelOrders.AE2_ROW) {
            return;
        }
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel where = FuelOrders.askedIn(player, payload.requester(), payload.row());
        if (where == null) {
            return;
        }
        BlockEntity be = SccParts.at(where, payload.requester(), FuelOrders.sideOf(payload.row()));
        if (!(be instanceof WirelessReceiverBlockEntity receiver)) {
            return;
        }
        if (row == FuelOrders.AE2_OFFER_ROW) {
            ReceiverBridges.offered(receiver, payload.plan(), where);
            return;
        }
        // Row -8: the plan for an SCC job AE2 asked for. Claim it and start the job.
        if (FuelOrders.claim(player, where, payload.requester(), payload.row())) {
            NetworkView view = receiver.network();
            if (view != null && payload.plan() != null && !payload.plan().isBlank()) {
                FuelOrders.accept(view, payload.plan(), where);
            }
        }
    }
}
