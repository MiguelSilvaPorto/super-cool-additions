package com.sccadd.ae2;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import com.sccadd.ae2.receiver.ReceiverBridges;
import de.scc.calculator.SccLog;
import de.scc.calculator.network.SccRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Registers the wireless receiver as an AE2 node, so it can be the bridge
 * between an SCC network and an AE2 network without a Network Antenna.
 * Only loaded when AE2 is present.
 */
public final class Ae2BridgeRegister {
    private Ae2BridgeRegister() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(Ae2BridgeRegister::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                SccRegistry.WIRELESS_RECEIVER_BE.get(),
                (be, context) -> (IInWorldGridNodeHost) ReceiverBridges.host(be)
        );
        SccLog.info("sccadd", "wireless receiver registers as an AE2 node");
    }
}
