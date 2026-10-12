package com.sccadd.ae2.receiver;

import com.sccadd.ae2.SccAe2PatternStore;
import de.scc.calculator.network.WirelessReceiverBlockEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * One {@link ReceiverBridge} per wireless receiver, created on demand and
 * removed with the block.
 */
public final class ReceiverBridges {
    private static final Map<WirelessReceiverBlockEntity, ReceiverBridge> BRIDGES = new WeakHashMap<>();

    private ReceiverBridges() {
    }

    public static synchronized Object host(WirelessReceiverBlockEntity be) {
        return bridge(be);
    }

    public static synchronized void tick(WirelessReceiverBlockEntity be) {
        bridge(be).tick();
    }

    public static synchronized void removed(WirelessReceiverBlockEntity be) {
        ReceiverBridge bridge = BRIDGES.remove(be);
        if (bridge != null) {
            bridge.destroy();
        }
        SccAe2PatternStore.clear(be);
    }

    public static synchronized void offered(WirelessReceiverBlockEntity be, String text, ServerLevel where) {
        bridge(be).offered(text, where);
    }

    @org.jetbrains.annotations.Nullable
    public static synchronized ReceiverMeConnector connector(WirelessReceiverBlockEntity be) {
        return bridge(be).meConnector();
    }

    private static ReceiverBridge bridge(WirelessReceiverBlockEntity be) {
        return BRIDGES.computeIfAbsent(be, ReceiverBridge::new);
    }
}
