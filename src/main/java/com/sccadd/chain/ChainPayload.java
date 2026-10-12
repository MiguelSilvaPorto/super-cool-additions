package com.sccadd.chain;

import com.sccadd.SccAdditions;
import de.scc.calculator.network.RequesterBlockEntity;
import de.scc.calculator.network.RequesterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The chain drawn on a requester row, sent to the server when the editor closes or changes. */
public record ChainPayload(BlockPos pos, int row, CompoundTag chain) implements CustomPacketPayload {
    public static final Type<ChainPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SccAdditions.MODID, "requester_chain"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChainPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ChainPayload::pos,
            ByteBufCodecs.VAR_INT, ChainPayload::row,
            ByteBufCodecs.COMPOUND_TAG, ChainPayload::chain,
            ChainPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ChainPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof RequesterMenu menu)
                    || !menu.pos().equals(payload.pos())
                    || !(player.level().getBlockEntity(payload.pos()) instanceof RequesterBlockEntity be)
                    || payload.row() < 0 || payload.row() >= RequesterBlockEntity.ROWS) {
                return;
            }
            ChainData data = ChainData.load(payload.chain());
            for (ChainData.Node node : data.nodes) {
                node.have = 0L;
            }
            ((ChainHolder) be).sccadd$setChain(payload.row(), data);
        });
    }
}
