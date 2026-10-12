package com.sccadd.requester;

import de.scc.calculator.network.RequesterBlockEntity;
import de.scc.calculator.network.RequesterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sets the target and/or the order size of a requester row with full-size numbers (SCC's own menu
 * buttons carry only four digits). A negative value means "leave it as it is".
 */
public record RequesterTargetPayload(BlockPos pos, int row, long amount, long batch, long interval) implements CustomPacketPayload {
    public static final Type<RequesterTargetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("sccadd", "requester_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequesterTargetPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequesterTargetPayload::pos,
            ByteBufCodecs.VAR_INT, RequesterTargetPayload::row,
            ByteBufCodecs.VAR_LONG, RequesterTargetPayload::amount,
            ByteBufCodecs.VAR_LONG, RequesterTargetPayload::batch,
            ByteBufCodecs.VAR_LONG, RequesterTargetPayload::interval,
            RequesterTargetPayload::new);

    /** SCC stores the target and the order size in an int; its own logic works with this much. */
    public static final long INT_CAP = 1_000_000_000L;

    /** A week: longer than any sensible order interval. */
    public static final long MAX_SECONDS = 7L * 24L * 3600L;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequesterTargetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof RequesterMenu menu)
                    || !menu.pos().equals(payload.pos())
                    || payload.row() < 0 || payload.row() > 5
                    || !(player.level().getBlockEntity(payload.pos()) instanceof RequesterBlockEntity be)) {
                return;
            }
            if (payload.amount() > 0L) {
                long amount = Math.min(BigMath.MAX, payload.amount());
                ((BigRow) (Object) be.row(payload.row())).sccadd$setBigAmount(amount);
                be.setAmount(payload.row(), (int) Math.min(INT_CAP, amount));
            }
            if (payload.batch() >= 0L) {
                be.setBatch(payload.row(), (int) Math.min(INT_CAP, payload.batch()));
            }
            if (payload.interval() > 0L) {
                long seconds = Math.min(MAX_SECONDS, payload.interval());
                int nearest = 0;
                for (int i = 1; i < RequesterBlockEntity.INTERVALS.length; i++) {
                    if (Math.abs(RequesterBlockEntity.INTERVALS[i] - seconds) < Math.abs(RequesterBlockEntity.INTERVALS[nearest] - seconds)) {
                        nearest = i;
                    }
                }
                be.setInterval(payload.row(), nearest);
                ((BigRow) (Object) be.row(payload.row())).sccadd$setIntervalSeconds(seconds);
                be.setChanged();
                // SCC synced inside setInterval, before the free seconds were set: send them now.
                ((com.sccadd.mixin.NetworkBlockEntityInvoker) (Object) be).sccadd$sync();
            }
        });
    }
}
