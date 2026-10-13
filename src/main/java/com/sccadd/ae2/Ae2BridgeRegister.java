package com.sccadd.ae2;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import com.sccadd.ae2.power.FiberPowers;
import com.sccadd.ae2.power.FiberSelfTest;
import com.sccadd.ae2.receiver.ReceiverBridges;
import de.scc.calculator.SccLog;
import de.scc.calculator.network.NetworkBlockEntity;
import de.scc.calculator.network.SccPowerGrid;
import de.scc.calculator.network.SccRegistry;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Registers the wireless receiver as an AE2 node (the AE2 bridge without an
 * antenna) and the SCC network blocks as AE2 <em>power</em> nodes (the Quartz
 * Fiber bridge, ME to SCC). Only loaded when AE2 is present.
 */
public final class Ae2BridgeRegister {
    /** Colour of the "AE2" source in the SCC controller's power panel (AE2 violet). */
    private static final int AE2_COLOUR = 0xFFB061E6;

    private Ae2BridgeRegister() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(Ae2BridgeRegister::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, FiberPowers::serverTick);
        FiberSelfTest.register();
        SccPowerGrid.source("ae2", AE2_COLOUR);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                SccRegistry.WIRELESS_RECEIVER_BE.get(),
                (be, context) -> (IInWorldGridNodeHost) ReceiverBridges.host(be)
        );
        // The network "plumbing" of SCC: a Quartz Fiber on any of these feeds the
        // whole network. The antenna and the receiver already have nodes of their own.
        registerPowerHost(event, SccRegistry.CABLE_BE.get());
        registerPowerHost(event, SccRegistry.CABLE_BUS_BE.get());
        registerPowerHost(event, SccRegistry.SCC_STORAGE_BE.get());
        registerPowerHost(event, SccRegistry.STORAGE_CONNECTOR_BE.get());
        registerPowerHost(event, SccRegistry.CALCULATOR_CONTROLLER_BE.get());
        registerPowerHost(event, SccRegistry.INTERFACE_BE.get());
        SccLog.info("sccadd", "wireless receiver and SCC network blocks register as AE2 nodes");
    }

    private static <B extends BlockEntity> void registerPowerHost(RegisterCapabilitiesEvent event, BlockEntityType<B> type) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                type,
                (be, context) -> (IInWorldGridNodeHost) FiberPowers.host((NetworkBlockEntity) be)
        );
    }
}
