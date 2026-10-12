package com.sccadd.village;

import com.sccadd.SccAdditions;
import de.scc.calculator.network.SccTier;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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
 *
 * <p>One chamber per SCC tier, all sharing one block entity: SCC's upgrade items carry a chamber into the
 * next one and the block entity comes along, so what is inside is never lost. See {@link VillageTiers}.</p>
 */
public final class VillageRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SccAdditions.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SccAdditions.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SccAdditions.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, SccAdditions.MODID);

    /** The six chambers, one per SCC tier. The SCC upgrade items carry one into the next. */
    public static final Map<SccTier, DeferredBlock<VillageChamberBlock>> CHAMBERS = new EnumMap<>(SccTier.class);
    public static final Map<SccTier, DeferredItem<VillageChamberItem>> CHAMBER_ITEMS = new EnumMap<>(SccTier.class);

    static {
        for (SccTier tier : SccTier.values()) {
            String name = tier == SccTier.BASIC ? "village_chamber" : "village_chamber_" + tier.id();
            DeferredBlock<VillageChamberBlock> block = BLOCKS.register(name, () -> new VillageChamberBlock(chamberProps(), tier));
            CHAMBERS.put(tier, block);
            CHAMBER_ITEMS.put(tier, ITEMS.register(name, () -> new VillageChamberItem(block.get(), new Item.Properties(), tier)));
        }
    }

    /** The basic chamber: the one the others upgrade from. */
    public static final DeferredBlock<VillageChamberBlock> CHAMBER = CHAMBERS.get(SccTier.BASIC);
    public static final DeferredItem<VillageChamberItem> CHAMBER_ITEM = CHAMBER_ITEMS.get(SccTier.BASIC);

    public static final Supplier<BlockEntityType<VillageChamberBlockEntity>> CHAMBER_BE = BLOCK_ENTITIES.register("village_chamber",
            () -> BlockEntityType.Builder.of(VillageChamberBlockEntity::new,
                    CHAMBERS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
    public static final Supplier<MenuType<VillageMenu>> MENU = MENUS.register("village",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new VillageMenu(id, inventory, buf.readBlockPos())));

    private VillageRegistry() {
    }

    private static BlockBehaviour.Properties chamberProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.EMERALD).strength(2.5F)
                .sound(SoundType.GLASS).noOcclusion().isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false).isValidSpawn((state, level, pos, type) -> false);
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
            for (DeferredItem<VillageChamberItem> item : CHAMBER_ITEMS.values()) {
                event.accept(item.get());
            }
        }
    }
}
