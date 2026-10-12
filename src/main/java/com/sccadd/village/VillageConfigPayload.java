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

/** An action taken in the chamber's window. */
public record VillageConfigPayload(BlockPos pos, int action, int value, int extra) implements CustomPacketPayload {
    public static final int TRADE = 0;
    public static final int OUTPUT = 1;
    public static final int TAKE_VILLAGER = 2;
    public static final int TAKE_WORKSTATION = 3;
    public static final int REMOVE_MODULE = 4;

    public static final Type<VillageConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SccAdditions.MODID, "village_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VillageConfigPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, VillageConfigPayload::pos,
            ByteBufCodecs.VAR_INT, VillageConfigPayload::action,
            ByteBufCodecs.VAR_INT, VillageConfigPayload::value,
            ByteBufCodecs.VAR_INT, VillageConfigPayload::extra,
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
                    || !(player.level().getBlockEntity(payload.pos()) instanceof VillageChamberBlockEntity be)) {
                return;
            }
            switch (payload.action()) {
                case TRADE -> be.trade(payload.value(), Math.max(1, Math.min(64, payload.extra())), player);
                case OUTPUT -> be.setOutput(VillageChamberBlockEntity.Output.values()[Math.floorMod(payload.value(), VillageChamberBlockEntity.Output.values().length)]);
                case TAKE_VILLAGER -> be.takeVillagerOut(player);
                case TAKE_WORKSTATION -> be.takeWorkstationOut(player);
                case REMOVE_MODULE -> {
                    be.removeModule(player);
                    player.closeContainer();
                }
                default -> {
                }
            }
        });
    }
}
