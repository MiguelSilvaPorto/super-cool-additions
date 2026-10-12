package com.sccadd.village;

import com.sccadd.SccAdditions;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The village chamber, registered only when Easy Villagers is installed: a glass box on the SCC network
 * that takes Easy Villagers' Trader or Auto Trader inside and works as it.
 */
public final class VillageRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SccAdditions.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SccAdditions.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SccAdditions.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SccAdditions.MODID);

    public static final DeferredBlock<VillageChamberBlock> CHAMBER = BLOCKS.register("village_chamber",
            () -> new VillageChamberBlock(BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD).strength(2.5F)
                    .sound(SoundType.GLASS).noOcclusion().isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false).isValidSpawn((state, level, pos, type) -> false)));
    public static final DeferredItem<BlockItem> CHAMBER_ITEM = ITEMS.registerSimpleBlockItem("village_chamber", CHAMBER);
    public static final Supplier<BlockEntityType<VillageChamberBlockEntity>> CHAMBER_BE = BLOCK_ENTITIES.register("village_chamber",
            () -> BlockEntityType.Builder.of(VillageChamberBlockEntity::new, CHAMBER.get()).build(null));
    public static final Supplier<MenuType<VillageMenu>> MENU = MENUS.register("village",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new VillageMenu(id, inventory, buf.readBlockPos())));

    private VillageRegistry() {
    }

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(VillageRegistry::tabs);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(VillageSelfTest::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(VillageSelfTest::onStarted);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(CHAMBER_ITEM.get());
        }
    }
}
