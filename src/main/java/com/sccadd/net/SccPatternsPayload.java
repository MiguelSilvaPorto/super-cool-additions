package com.sccadd.net;

import com.sccadd.ae2.SccAe2PatternStore;
import com.sccadd.ae2.SccAe2Recipe;
import de.scc.calculator.network.NetworkBlockEntity;
import de.scc.calculator.network.SccWire;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * One chunk of the recipe text the client sends back with the offer answer.
 * Splitting it keeps every packet small no matter how many items a pack offers;
 * the server reassembles the chunks (see {@link SccPatternChunks}).
 */
public record SccPatternsPayload(BlockPos pos, int index, int count, String chunk) implements CustomPacketPayload {
    public static final Type<SccPatternsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("sccadd", "scc_patterns"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SccPatternsPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SccPatternsPayload::pos,
            ByteBufCodecs.VAR_INT, SccPatternsPayload::index,
            ByteBufCodecs.VAR_INT, SccPatternsPayload::count,
            SccWire.TEXT, SccPatternsPayload::chunk,
            SccPatternsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SccPatternsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SccPatternChunks.accept(player, payload);
            }
        });
    }

    /** Server side: reassembles the chunks and stores the recipes. */
    static void store(ServerPlayer player, SccPatternsPayload payload, String text) {
        ServerLevel level = player.serverLevel();
        BlockEntity be = level.getBlockEntity(payload.pos());
        if (be == null) {
            // The bridge may live in another dimension than the answering player.
            for (ServerLevel other : player.server.getAllLevels()) {
                be = other.getBlockEntity(payload.pos());
                if (be != null) {
                    break;
                }
            }
        }
        if (be instanceof de.scc.calculator.network.WirelessReceiverBlockEntity receiver) {
            com.sccadd.ae2.receiver.ReceiverBridges.patternsArrived(receiver, text);
        } else if (be instanceof NetworkBlockEntity network) {
            SccAe2PatternStore.setRecipes(network, SccAe2Recipe.parse(text));
        }
    }
}
