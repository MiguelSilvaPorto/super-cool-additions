package com.sccadd.village.client;

import com.sccadd.village.VillageRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client side of the village blocks: the villager drawn in the glass box, and the Auto Trader's window. */
public final class VillageClient {
    private VillageClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(VillageClient::renderers);
        modBus.addListener(VillageClient::screens);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(VillageRegistry.CHAMBER_BE.get(), context -> new VillageChamberRenderer(context));
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(VillageRegistry.MENU.get(), VillageScreen::new);
    }
}
