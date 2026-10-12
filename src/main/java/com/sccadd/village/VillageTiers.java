package com.sccadd.village;

import de.scc.calculator.network.SccTier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import org.jetbrains.annotations.Nullable;

/**
 * The bridge between SCC's tier questions and the chambers. SCC's {@code SccRegistry} asks these through
 * {@code SccRegistryTierMixin}, so the upgrade items, the Calculator Controller's upgrade planner and every
 * tier display treat a chamber like any other SCC machine.
 *
 * <p>Everything here answers {@code null} for blocks that are not chambers, so SCC keeps its own answers.</p>
 */
public final class VillageTiers {
    private VillageTiers() {
    }

    /** Which tier a chamber is, or {@code null} when the block is not a chamber. */
    @Nullable
    public static SccTier tierOf(Block block) {
        return block instanceof VillageChamberBlock chamber ? chamber.tier() : null;
    }

    /** The chamber of the wanted tier, or {@code null} when the block is not a chamber (or has no such tier). */
    @Nullable
    public static Block family(Block block, SccTier target) {
        if (!(block instanceof VillageChamberBlock)) {
            return null;
        }
        DeferredBlock<VillageChamberBlock> holder = VillageRegistry.CHAMBERS.get(target);
        return holder == null ? null : holder.get();
    }
}
