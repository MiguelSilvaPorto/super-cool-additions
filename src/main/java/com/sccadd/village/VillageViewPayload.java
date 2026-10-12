package com.sccadd.village;

import com.sccadd.SccAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** What the chamber's window shows, from the server. */
public record VillageViewPayload(BlockPos pos, VillageView view) implements CustomPacketPayload {
    public static final Type<VillageViewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SccAdditions.MODID, "village_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VillageViewPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, VillageViewPayload::pos,
            VillageView.CODEC, VillageViewPayload::view,
            VillageViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VillageViewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.sccadd.village.client.VillageClientHooks.accept(payload.pos(), payload.view()));
    }
}
