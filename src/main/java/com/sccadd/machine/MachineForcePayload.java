package com.sccadd.machine;

import com.sccadd.SccAdditions;
import de.scc.calculator.network.ProcessingCalculatorBlockEntity;
import de.scc.calculator.network.ProcessorConfigMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Which machine a Processing Calculator should act as; empty = detect it from the block in front. */
public record MachineForcePayload(BlockPos pos, String machine) implements CustomPacketPayload {
    public static final Type<MachineForcePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SccAdditions.MODID, "machine_force"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MachineForcePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MachineForcePayload::pos,
            ByteBufCodecs.stringUtf8(200), MachineForcePayload::machine,
            MachineForcePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MachineForcePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof ProcessorConfigMenu menu)
                    || !menu.pos().equals(payload.pos())
                    || !(player.level().getBlockEntity(payload.pos()) instanceof ProcessingCalculatorBlockEntity be)) {
                return;
            }
            ResourceLocation id = payload.machine().isBlank() ? null : ResourceLocation.tryParse(payload.machine());
            ((MachineForce) be).sccadd$setForced(id);
        });
    }
}
