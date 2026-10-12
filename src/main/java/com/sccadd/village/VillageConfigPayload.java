package com.sccadd.village;

import com.sccadd.SccAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** An action taken in the chamber's window; the server answers with the window's new {@link VillageView}. */
public record VillageConfigPayload(BlockPos pos, int action, int value, int extra, String text) implements CustomPacketPayload {
    public static final int TRADE = 0;
    public static final int OUTPUT = 1;
    public static final int TAKE_VILLAGER = 2;
    public static final int TAKE_WORKSTATION = 3;
    public static final int REMOVE_MODULE = 4;
    public static final int REFRESH = 5;
    public static final int STORE_CURRENT = 6;
    public static final int USE_VILLAGER = 7;
    public static final int TAKE_FROM_SYSTEM = 8;
    public static final int XP_MODE = 9;
    public static final int USE_BENCH = 10;
    public static final int RENAME = 11;
    public static final int KEEP = 12;

    public static final Type<VillageConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SccAdditions.MODID, "village_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VillageConfigPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, VillageConfigPayload::pos,
            ByteBufCodecs.VAR_INT, VillageConfigPayload::action,
            ByteBufCodecs.VAR_INT, VillageConfigPayload::value,
            ByteBufCodecs.VAR_INT, VillageConfigPayload::extra,
            ByteBufCodecs.stringUtf8(1024), VillageConfigPayload::text,
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
            boolean close = false;
            switch (payload.action()) {
                case TRADE -> be.trade(payload.value(), Math.max(1, Math.min(64, payload.extra())), player);
                case OUTPUT -> be.setOutput(VillageChamberBlockEntity.Output.values()[Math.floorMod(payload.value(), VillageChamberBlockEntity.Output.values().length)]);
                case TAKE_VILLAGER -> be.takeVillagerOut(player);
                case TAKE_WORKSTATION -> be.takeWorkstationOut(player);
                case REMOVE_MODULE -> {
                    be.removeModule(player);
                    close = true;
                }
                case REFRESH -> be.refresh();
                case STORE_CURRENT -> be.storeCurrent(player);
                case USE_VILLAGER -> be.useVillager(payload.text(), player);
                case TAKE_FROM_SYSTEM -> be.takeVillager(payload.text(), player);
                case USE_BENCH -> be.useBench(payload.text(), player);
                case RENAME -> be.rename(payload.text());
                case KEEP -> be.setKeep(payload.value(), payload.extra(), player);
                case XP_MODE -> be.setXpMode(VillageChamberBlockEntity.XpMode.values()[Math.floorMod(payload.value(), VillageChamberBlockEntity.XpMode.values().length)]);
                default -> {
                }
            }
            if (close) {
                player.closeContainer();
            } else {
                PacketDistributor.sendToPlayer(player, new VillageViewPayload(payload.pos(), be.buildView()));
            }
        });
    }
}
