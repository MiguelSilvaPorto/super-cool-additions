package com.sccadd;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

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
        if (ModList.get().isLoaded("ae2")) {
            // Loaded only when AE2 is present; the class references AE2 types.
            com.sccadd.ae2.Ae2BridgeRegister.init(modBus);
        }
    }
}
