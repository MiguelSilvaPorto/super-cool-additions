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
 * The village blocks, registered only when Easy Villagers is installed: a glass box with a villager
 * in it, standing on the SCC network. The Trader is for you (it pays from the network); the Auto
 * Trader works alone on the offers you pick.
 */
public final class VillageRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SccAdditions.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SccAdditions.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SccAdditions.MODID);

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SccAdditions.MODID);

    public static final Supplier<MenuType<VillageMenu>> MENU = MENUS.register("village",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new VillageMenu(id, inventory, buf.readBlockPos())));

    private static BlockBehaviour.Properties glass() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD).strength(2.5F).sound(SoundType.GLASS)
                .noOcclusion().isViewBlocking((state, level, pos) -> false).isSuffocating((state, level, pos) -> false)
                .isValidSpawn((state, level, pos, type) -> false);
    }

    public static final DeferredBlock<VillageTraderBlock> TRADER = BLOCKS.register("village_trader", () -> new VillageTraderBlock(glass()));
    public static final DeferredBlock<VillageAutoTraderBlock> AUTO_TRADER = BLOCKS.register("village_auto_trader", () -> new VillageAutoTraderBlock(glass()));
    public static final DeferredItem<BlockItem> TRADER_ITEM = ITEMS.registerSimpleBlockItem("village_trader", TRADER);
    public static final DeferredItem<BlockItem> AUTO_TRADER_ITEM = ITEMS.registerSimpleBlockItem("village_auto_trader", AUTO_TRADER);
    public static final Supplier<BlockEntityType<VillageSimulatorBlockEntity>> VILLAGE_BE = BLOCK_ENTITIES.register("village",
            () -> BlockEntityType.Builder.of(VillageSimulatorBlockEntity::new, TRADER.get(), AUTO_TRADER.get()).build(null));

    private VillageRegistry() {
    }

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(VillageRegistry::tabs);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(VillageSelfTest::register);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(TRADER_ITEM.get());
            event.accept(AUTO_TRADER_ITEM.get());
        }
    }
}
