package com.sccadd.mixin.client;

import com.sccadd.SccAddConfig;
import com.sccadd.ae2.client.SccPatternWriter;
import com.sccadd.net.SccPatternsPayload;
import de.scc.calculator.client.RequesterPlanner;
import de.scc.calculator.network.PlanRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the moment the offer answer is built: the item list goes back the normal
 * way, and the recipe lines follow as small chunks ({@link SccPatternsPayload}),
 * so even a huge pack never produces one oversized packet.
 */
@Mixin(value = RequesterPlanner.class, remap = false)
public abstract class Ae2OfferPatternsMixin {
    private static final int CHUNK_CHARS = 60_000;

    @Inject(method = "compute", at = @At("HEAD"), require = 0)
    private static void sccadd$offerTarget(PlanRequestPayload payload, CallbackInfoReturnable<Object> cir) {
        SccPatternWriter.setTarget(payload.requester());
        SccPatternWriter.setStock(payload.stock());
    }

    @Inject(method = "compute", at = @At("RETURN"), require = 0)
    private static void sccadd$clearOfferTarget(PlanRequestPayload payload, CallbackInfoReturnable<Object> cir) {
        SccPatternWriter.clearTarget();
    }

    @Redirect(
            method = "offer",
            at = @At(value = "INVOKE", target = "Ljava/lang/StringBuilder;toString()Ljava/lang/String;"),
            require = 0
    )
    private static String sccadd$sendPatternChunks(StringBuilder sb) {
        String base = sb.toString();
        if (!SccAddConfig.sccPatternsInAe2() && !SccAddConfig.offerRequiresStock()) {
            return base;
        }
        try {
            base = SccPatternWriter.filterOffer(base);
            BlockPos target = SccPatternWriter.target();
            String patterns = SccPatternWriter.patternsFor(base);
            if (target != null && patterns.isEmpty()) {
                // An empty set still has to arrive: it replaces the previous patterns atomically.
                SccPatternsPayload empty = new SccPatternsPayload(target, 0, 1, "");
                Minecraft.getInstance().execute(() -> PacketDistributor.sendToServer(empty));
            } else if (target != null) {
                int count = (patterns.length() + CHUNK_CHARS - 1) / CHUNK_CHARS;
                Minecraft minecraft = Minecraft.getInstance();
                for (int index = 0; index < count; index++) {
                    String part = patterns.substring(index * CHUNK_CHARS,
                            Math.min(patterns.length(), (index + 1) * CHUNK_CHARS));
                    SccPatternsPayload payload = new SccPatternsPayload(target, index, count, part);
                    minecraft.execute(() -> PacketDistributor.sendToServer(payload));
                }
            }
        } catch (Throwable failed) {
            // never break the offer because of the addon
        }
        return base;
    }
}
