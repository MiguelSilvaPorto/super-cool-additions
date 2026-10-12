package com.sccadd.village.client;

import com.sccadd.village.VillageChamberBlock;
import com.sccadd.village.VillageChamberItem;
import com.sccadd.village.VillageRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/** Client side of the village blocks: the villager drawn in the glass box, the Auto Trader's window, and the tier colour. */
public final class VillageClient {
    private VillageClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(VillageClient::renderers);
        modBus.addListener(VillageClient::screens);
        modBus.addListener(VillageClient::blockColors);
        modBus.addListener(VillageClient::itemColors);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(VillageRegistry.CHAMBER_BE.get(), context -> new VillageChamberRenderer(context));
    }

    private static void screens(RegisterMenuScreensEvent event) {
        event.register(VillageRegistry.MENU.get(), VillageScreen::new);
    }

    /** The floor of the box takes the colour of the tier, the way SCC's machines do. */
    private static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> tint == 0 && state.getBlock() instanceof VillageChamberBlock chamber
                ? chamber.tier().color() : -1,
                VillageRegistry.CHAMBERS.values().stream().map(DeferredBlock::get).toArray(Block[]::new));
    }

    private static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 && stack.getItem() instanceof VillageChamberItem item
                ? item.tier().color() : -1,
                VillageRegistry.CHAMBER_ITEMS.values().stream().map(DeferredItem::get).toArray(Item[]::new));
    }
}
