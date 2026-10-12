package com.sccadd.village;

import com.sccadd.SccAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A change made in the Auto Trader's window. */
public record VillageConfigPayload(BlockPos pos, int action, String signature, int value) implements CustomPacketPayload {
    public static final int PICK = 0;
    public static final int OUTPUT = 1;
    public static final int LEVEL_UP = 2;

    public static final Type<VillageConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SccAdditions.MODID, "village_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VillageConfigPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, VillageConfigPayload::pos,
            ByteBufCodecs.VAR_INT, VillageConfigPayload::action,
            ByteBufCodecs.stringUtf8(2048), VillageConfigPayload::signature,
            ByteBufCodecs.VAR_INT, VillageConfigPayload::value,
            VillageConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VillageConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof VillageMenu menu)
                    || !menu.pos().equals(payload.pos())
                    || !(player.level().getBlockEntity(payload.pos()) instanceof VillageSimulatorBlockEntity be)) {
                return;
            }
            switch (payload.action()) {
                case PICK -> be.pick(payload.signature(), payload.value() >= 0, Math.max(0, payload.value()));
                case OUTPUT -> be.setOutput(payload.value() == 1 ? VillageSimulatorBlockEntity.Output.NEARBY : VillageSimulatorBlockEntity.Output.NETWORK);
                case LEVEL_UP -> be.setLevelUp(payload.value() != 0);
                default -> {
                }
            }
        });
    }
}
