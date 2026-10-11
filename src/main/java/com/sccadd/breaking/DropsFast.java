package com.sccadd.breaking;

import com.sccadd.SccAddConfig;
import de.scc.calculator.SccLog;
import de.scc.calculator.SccProfiler;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * The two expensive pieces of a simulated block break:
 *
 * <ol>
 *   <li>The loot roll. Origin, tool, player and position are identical for every
 *       block of one tick, so the {@link LootParams} are built once per block state
 *       instead of once per block.</li>
 *   <li>The {@link BlockDropsEvent}. It is posted with the direct constructor
 *       instead of SCC's reflection, and the whole event can be switched off for
 *       players who do not use drop-modifying enchantments.</li>
 * </ol>
 */
public final class DropsFast {
    /** One cache per Breaking Calculator (weak, so removed machines do not pile up). */
    private static final Map<Object, Cache> CACHES = new WeakHashMap<>();

    private DropsFast() {
    }

    private static final class Cache {
        long tick = Long.MIN_VALUE;
        BlockPos pos;
        ItemStack tool;
        Entity entity;
        final Map<BlockState, LootParams> byState = new IdentityHashMap<>();

        void reset(long tick, BlockPos pos, ItemStack tool, Entity entity) {
            this.tick = tick;
            this.pos = pos;
            this.tool = tool;
            this.entity = entity;
            this.byState.clear();
        }
    }

    /** Drop-in replacement for {@code Block.getDrops(state, level, pos, be, entity, tool)}. */
    public static List<ItemStack> getDrops(Object owner, BlockState state, ServerLevel level, BlockPos pos,
                                           BlockEntity be, Entity entity, ItemStack tool) {
        if (!SccAddConfig.cacheLootParams()) {
            return Block.getDrops(state, level, pos, be, entity, tool);
        }
        long profile = SccAddConfig.profile() ? SccProfiler.begin() : 0L;
        try {
            Cache cache = CACHES.computeIfAbsent(owner, key -> new Cache());
            long now = level.getGameTime();
            if (cache.tick != now || cache.pos != pos || cache.tool != tool || cache.entity != entity) {
                cache.reset(now, pos, tool, entity);
            }
            // Exact same parameters as Block.getDrops builds, but only once per state per tick.
            LootParams params = cache.byState.get(state);
            if (params == null) {
                params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                        .withParameter(LootContextParams.TOOL, tool)
                        .withOptionalParameter(LootContextParams.THIS_ENTITY, entity)
                        .withOptionalParameter(LootContextParams.BLOCK_ENTITY, be)
                        .withParameter(LootContextParams.BLOCK_STATE, state)
                        .create(LootContextParamSets.BLOCK);
                cache.byState.put(state, params);
            }
            ResourceKey<LootTable> tableKey = state.getBlock().getLootTable();
            if (tableKey == BuiltInLootTables.EMPTY) {
                return List.of();
            }
            return level.getServer().reloadableRegistries().getLootTable(tableKey).getRandomItems(params);
        } catch (Throwable failed) {
            SccLog.caught("sccadd", "fast loot roll for " + state, failed);
            return Block.getDrops(state, level, pos, be, entity, tool);
        } finally {
            if (profile != 0L) {
                SccProfiler.end("sccadd loot roll", profile);
            }
        }
    }

    /**
     * Drop-in replacement for SCC's reflective {@code DropEvents.apply}:
     * same event, same order, but constructed directly and optionally skipped.
     */
    public static List<ItemStack> applyDropsEvent(ServerLevel server, BlockPos pos, BlockState state,
                                                  List<ItemStack> drops, ItemStack tool, Entity breaker) {
        if (!SccAddConfig.fireDropsEvent()) {
            return drops;
        }
        if (server == null || drops.isEmpty() || breaker == null) {
            return drops;
        }
        long profile = SccAddConfig.profile() ? SccProfiler.begin() : 0L;
        try {
            List<ItemEntity> carriers = new ArrayList<>(drops.size());
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    carriers.add(new ItemEntity(server, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop.copy()));
                }
            }
            BlockDropsEvent event = new BlockDropsEvent(server, pos, state, null, carriers, breaker, tool);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled()) {
                return List.of();
            }
            List<ItemStack> out = new ArrayList<>(event.getDrops().size());
            for (ItemEntity carrier : event.getDrops()) {
                ItemStack stack = carrier.getItem();
                if (!stack.isEmpty()) {
                    out.add(stack.copy());
                }
            }
            return out;
        } catch (Throwable failed) {
            SccLog.caught("sccadd", "drop event for " + state, failed);
            return drops;
        } finally {
            if (profile != 0L) {
                SccProfiler.end("sccadd drop event", profile);
            }
        }
    }
}
