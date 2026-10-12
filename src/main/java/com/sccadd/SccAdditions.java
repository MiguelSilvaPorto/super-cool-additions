package com.sccadd;

import com.sccadd.net.SccPatternsPayload;
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
        if (ModList.get().isLoaded("ae2")) {
            // Loaded only when AE2 is present; the class references AE2 types.
            com.sccadd.ae2.Ae2BridgeRegister.init(modBus);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SccPatternsPayload.TYPE, SccPatternsPayload.CODEC, SccPatternsPayload::handle);
    }
}
