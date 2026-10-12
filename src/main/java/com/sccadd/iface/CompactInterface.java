package com.sccadd.iface;

import com.mojang.serialization.MapCodec;
import com.sccadd.SccAdditions;
import de.scc.calculator.network.InterfaceBlock;
import de.scc.calculator.network.SccRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Interface in a small body, like AE2's: a plate with a short stub, still all nine slots, the tanks and the
 * same window. It is SCC's own Interface block with another shape and model; its block entity is the Interface's
 * (the block is added to that type's valid blocks), so everything that works on an Interface works on this.
 */
public final class CompactInterface extends InterfaceBlock {
    public static final MapCodec<CompactInterface> CODEC = simpleCodec(CompactInterface::new);

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(SccAdditions.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(SccAdditions.MODID);

    public static final DeferredBlock<CompactInterface> BLOCK = BLOCKS.register("interface_compact",
            () -> new CompactInterface(BlockBehaviour.Properties.ofFullCopy(SccRegistry.INTERFACE.get()).noOcclusion()));
    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem("interface_compact", BLOCK);

    private static final VoxelShape NORTH = Shapes.or(Block.box(2, 2, 0, 14, 14, 3), Block.box(4, 4, 3, 12, 12, 10));
    private static final VoxelShape EAST = turn(NORTH);
    private static final VoxelShape SOUTH = turn(EAST);
    private static final VoxelShape WEST = turn(SOUTH);

    public CompactInterface(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(CompactInterface::addToType);
        modBus.addListener(CompactInterface::tabs);
    }

    /** The Interface's block entity type has to list this block, or the game refuses to create it. */
    private static void addToType(BlockEntityTypeAddBlocksEvent event) {
        event.modify(SccRegistry.INTERFACE_BE.get(), BLOCK.get());
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ITEM.get());
        }
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        return switch (facing) {
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    /** A quarter turn clockwise seen from above, the way a blockstate "y": 90 turns the model. */
    private static VoxelShape turn(VoxelShape shape) {
        VoxelShape[] out = {Shapes.empty()};
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) ->
                out[0] = Shapes.or(out[0], Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2)));
        return out[0];
    }
}
