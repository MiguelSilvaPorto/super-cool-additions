package com.sccadd.client;

import com.sccadd.iface.FilterWindow;
import de.scc.calculator.client.screen.StorageConnectorScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Closing the Storage Connector's window puts its filter back on page one, so nothing else sees a shifted filter. */
@EventBusSubscriber(modid = "sccadd", value = Dist.CLIENT)
public final class FilterPageReset {
    private FilterPageReset() {
    }

    @SubscribeEvent
    public static void closing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof StorageConnectorScreen screen) {
            var be = ((com.sccadd.mixin.client.StorageConnectorScreenAccess) (Object) screen).sccadd$connector();
            if (be != null) {
                ((FilterWindow) (Object) be.filter()).sccadd$setBase(0);
            }
        }
    }
}
