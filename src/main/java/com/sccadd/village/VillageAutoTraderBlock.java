package com.sccadd.village;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/** Automatic: trades the offers you pick on its own and delivers the results to the network or beside it. */
public class VillageAutoTraderBlock extends VillageBlockBase {
    public static final MapCodec<VillageAutoTraderBlock> CODEC = simpleCodec(VillageAutoTraderBlock::new);

    public VillageAutoTraderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public boolean isAuto() {
        return true;
    }
}
