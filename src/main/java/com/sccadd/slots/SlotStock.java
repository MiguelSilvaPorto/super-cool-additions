package com.sccadd.slots;

import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.NetworkAntennaBlockEntity;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.SccSecurity;
import de.scc.calculator.network.SccWire;
import de.scc.calculator.network.WirelessAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The stock of the player's SCC network, for the slot rule pop-ups: the client asks, the server answers
 * with one line per item ({@code I<TAB>encoded key<TAB>count}) and per fluid ({@code F<TAB>id<TAB>amount}).
 */
public final class SlotStock {
    private static final int MAX_LINES = 6000;

    private SlotStock() {
    }

    /** Client -> server. */
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("sccadd", "slot_stock_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Request payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    PacketDistributor.sendToPlayer(player, new Answer(collect(player)));
                }
            });
        }
    }

    /** Server -> client. */
    public record Answer(String text) implements CustomPacketPayload {
        public static final Type<Answer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("sccadd", "slot_stock"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Answer> CODEC = StreamCodec.composite(
                SccWire.TEXT, Answer::text, Answer::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Answer payload, IPayloadContext context) {
            context.enqueueWork(() -> SlotStockClient.set(payload.text()));
        }
    }

    private static String collect(ServerPlayer player) {
        ItemStack terminal = WirelessAccess.findAny(player);
        if (terminal.isEmpty()) {
            return "";
        }
        NetworkAntennaBlockEntity antenna = WirelessAccess.antenna(player, terminal);
        if (antenna == null || !WirelessAccess.inRange(player, antenna, WirelessAccess.linkedDim(terminal))) {
            return "";
        }
        NetworkView view = antenna.network();
        if (view == null || !view.online() || !SccSecurity.may(player, view, SccSecurity.Right.TAKE)) {
            return "";
        }
        NetworkView.Snapshot snapshot = view.snapshot(player.level().getGameTime());
        List<Map.Entry<StackKey, Long>> items = new ArrayList<>(snapshot.contents().entrySet());
        items.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        StringBuilder out = new StringBuilder();
        int lines = 0;
        for (Map.Entry<Fluid, Long> fluid : snapshot.fluids().entrySet()) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getKey());
            if (id != null && fluid.getValue() > 0L) {
                out.append("F\t").append(id).append('\t').append(fluid.getValue()).append('\n');
                lines++;
            }
        }
        for (Map.Entry<StackKey, Long> item : items) {
            if (lines++ >= MAX_LINES) {
                break;
            }
            if (item.getValue() > 0L) {
                out.append("I\t").append(item.getKey().encode()).append('\t').append(item.getValue()).append('\n');
            }
        }
        return out.toString();
    }
}
