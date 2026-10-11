package com.sccadd;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Super Cool Additions - an addon for SCC (Super Cool Calculator).
 *
 * <p>Two goals: make the Breaking Calculator cheap enough to spam, and integrate
 * SCC and AE2 deeply in both directions (recipes and items).</p>
 */
@Mod(SccAdditions.MODID)
public final class SccAdditions {
    public static final String MODID = "sccadd";

    public SccAdditions(IEventBus modBus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, SccAddConfig.SPEC);
    }
}
