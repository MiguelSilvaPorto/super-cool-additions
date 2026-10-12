package com.sccadd.village;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;

/** Manual: opens the villager's trade screen and pays from the network instead of your inventory. */
public class VillageTraderBlock extends VillageBlockBase {
    public static final MapCodec<VillageTraderBlock> CODEC = simpleCodec(VillageTraderBlock::new);

    public VillageTraderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public boolean isAuto() {
        return false;
    }
}
