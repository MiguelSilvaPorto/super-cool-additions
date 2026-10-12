package com.sccadd;

import com.sccadd.net.SccPatternsPayload;
import com.sccadd.requester.RequesterTargetPayload;
import com.sccadd.slots.SlotRulesPayload;
import com.sccadd.slots.SlotStock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Super Cool Additions - an addon for SCC (Super Cool Calculator).
 *
 * <p>Three goals: make the Breaking Calculator cheap enough to spam, integrate
 * SCC and AE2 deeply in both directions, and keep the recipe analysis usable
 * (resilient cache, smarter planner pick).</p>
 */
@Mod(SccAdditions.MODID)
public final class SccAdditions {
    public static final String MODID = "sccadd";

    public SccAdditions(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, SccAddConfig.SPEC);
        modBus.addListener(SccAdditions::registerPayloads);
        modBus.addListener(SccAdditions::registerCapabilities);
        if (ModList.get().isLoaded("easy_villagers")) {
            // The Village Simulator needs Easy Villagers' villager items.
            com.sccadd.village.VillageRegistry.init(modBus);
        }
        if (ModList.get().isLoaded("ae2")) {
            // Loaded only when AE2 is present; the class references AE2 types.
            com.sccadd.ae2.Ae2BridgeRegister.init(modBus);
        }
    }

    private static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
                de.scc.calculator.network.SccRegistry.INTERFACE_BE.get(),
                (be, side) -> ((com.sccadd.iface.TankHolder) be).sccadd$tanks());
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SccPatternsPayload.TYPE, SccPatternsPayload.CODEC, SccPatternsPayload::handle);
        registrar.playToServer(com.sccadd.chain.ChainPayload.TYPE, com.sccadd.chain.ChainPayload.CODEC, com.sccadd.chain.ChainPayload::handle);
        registrar.playToServer(com.sccadd.machine.MachineForcePayload.TYPE, com.sccadd.machine.MachineForcePayload.CODEC, com.sccadd.machine.MachineForcePayload::handle);
        registrar.playToServer(SlotRulesPayload.TYPE, SlotRulesPayload.CODEC, SlotRulesPayload::handle);
        registrar.playToServer(RequesterTargetPayload.TYPE, RequesterTargetPayload.CODEC, RequesterTargetPayload::handle);
        registrar.playToServer(SlotStock.Request.TYPE, SlotStock.Request.CODEC, SlotStock.Request::handle);
        registrar.playToClient(SlotStock.Answer.TYPE, SlotStock.Answer.CODEC, SlotStock.Answer::handle);
    }
}
