package com.sccadd.think;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Steps the thinking tasks a few milliseconds each client tick. */
@EventBusSubscriber(modid = "sccadd", value = Dist.CLIENT)
public final class ThinkTicker {
    private ThinkTicker() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ThinkQueue.tick();
    }
}
