package com.sccadd.village;

import com.sccadd.SccAdditions;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Village Simulator, registered only when Easy Villagers is installed: a villager that lives in
 * a block on the SCC network, trades with the network's stock, levels up on emerald trades and
 * delivers the trade you asked for to the system or to the storage beside it.
 */
public final class VillageRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SccAdditions.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SccAdditions.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SccAdditions.MODID);

    public static final DeferredBlock<VillageSimulatorBlock> SIMULATOR = BLOCKS.register("village_simulator",
            () -> new VillageSimulatorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD)
                    .strength(2.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> SIMULATOR_ITEM = ITEMS.registerSimpleBlockItem("village_simulator", SIMULATOR);
    public static final Supplier<BlockEntityType<VillageSimulatorBlockEntity>> SIMULATOR_BE = BLOCK_ENTITIES.register("village_simulator",
            () -> BlockEntityType.Builder.of(VillageSimulatorBlockEntity::new, SIMULATOR.get()).build(null));

    private VillageRegistry() {
    }

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(VillageRegistry::tabs);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(VillageSelfTest::register);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(SIMULATOR_ITEM.get());
        }
    }
}
