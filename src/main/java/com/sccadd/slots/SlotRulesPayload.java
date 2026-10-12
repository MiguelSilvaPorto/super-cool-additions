package com.sccadd.slots;

import de.scc.calculator.network.SccNotices;
import de.scc.calculator.network.SccWire;
import de.scc.calculator.network.WirelessAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The client's slot rules, to be saved on the player's wireless terminal. */
public record SlotRulesPayload(String rules) implements CustomPacketPayload {
    public static final Type<SlotRulesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("sccadd", "slot_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SlotRulesPayload> CODEC = StreamCodec.composite(
            SccWire.TEXT, SlotRulesPayload::rules, SlotRulesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SlotRulesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ItemStack terminal = WirelessAccess.findAny(player);
                if (terminal.isEmpty()) {
                    SccNotices.say(player, Component.translatable("scc.msg.wireless.none"));
                    return;
                }
                SlotRules.saveTo(terminal, SlotRules.decode(payload.rules()));
            }
        });
    }
}
