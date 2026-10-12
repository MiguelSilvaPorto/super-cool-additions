package com.sccadd.breaking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;

/**
 * "Dice tables" for simulated block breaks. For each block and tool (with its enchantments, fortune
 * included) it keeps a ring of real results of the full break pipeline (loot table, drop event,
 * smelting). Once enough real results are in, the drops of N blocks are summed from N random draws of
 * that ring instead of simulating N breaks. Every batch also feeds one fresh real result back, so the
 * ring keeps following the real loot.
 *
 * <p>Each sample is stored as (template id, count) pairs over the model's own stack templates, so a
 * sum of N blocks is N array increments instead of N component comparisons.</p>
 */
public final class DropModels {
    public static final int RING = 256;
    public static final int READY = 48;
    private static final int MAX_MODELS = 512;

    private record Key(Block block, Item tool, int enchantments) {
    }

    /** One table of a block and tool: what it can drop and what it really dropped so far. */
    public record TableInfo(String block, String tool, int samples, boolean ready, long learningMs) {
    }

    public static final class Model {
        /** Every distinct stack this block can drop (item + components), count 1. */
        private final List<ItemStack> templates = new ArrayList<>(4);
        /** Ring: the template ids of each unit of a sample, and their counts. */
        private final List<int[]> sampleIds = new ArrayList<>(RING);
        private final List<int[]> sampleCounts = new ArrayList<>(RING);
        private int next;

        private boolean announced;
        private long startedAt = System.currentTimeMillis();

        /** Scratch for sum(): draws per ring slot and the totals per template. */
        private int[] hits;
        private long[] totals;

        synchronized int samples() {
            return this.sampleIds.size();
        }

        public synchronized boolean ready() {
            return this.sampleIds.size() >= READY;
        }

        /** True once, when this table has just collected enough samples to be used. */
        synchronized boolean becameReady() {
            if (!this.announced && this.sampleIds.size() >= READY) {
                this.announced = true;
                return true;
            }
            return false;
        }

        synchronized long learningMillis() {
            return System.currentTimeMillis() - this.startedAt;
        }

        synchronized void add(List<ItemStack> drops) {
            List<ItemStack> kinds = new ArrayList<>(4);
            List<Integer> counts = new ArrayList<>(4);
            for (ItemStack stack : drops) {
                if (stack.isEmpty()) {
                    continue;
                }
                int at = -1;
                for (int i = 0; i < kinds.size(); i++) {
                    if (ItemStack.isSameItemSameComponents(kinds.get(i), stack)) {
                        at = i;
                        break;
                    }
                }
                if (at < 0) {
                    kinds.add(stack.copyWithCount(1));
                    counts.add(stack.getCount());
                } else {
                    counts.set(at, counts.get(at) + stack.getCount());
                }
            }
            int size = kinds.size();
            if (size == 0) {
                return;
            }
            int[] ids = new int[size];
            int[] many = new int[size];
            for (int i = 0; i < size; i++) {
                ids[i] = this.templateOf(kinds.get(i));
                many[i] = counts.get(i);
            }
            if (this.sampleIds.size() < RING) {
                this.sampleIds.add(ids);
                this.sampleCounts.add(many);
            } else {
                this.sampleIds.set(this.next, ids);
                this.sampleCounts.set(this.next, many);
                this.next = (this.next + 1) % RING;
            }
        }

        /** The summed drops of {@code blocks} simulated breaks, merged into as few stacks as possible. */
        public synchronized List<ItemStack> sum(int blocks) {
            int size = this.sampleIds.size();
            if (size == 0) {
                return List.of();
            }
            if (this.hits == null || this.hits.length < size) {
                this.hits = new int[Math.max(size, RING)];
            }
            if (this.totals == null || this.totals.length < this.templates.size()) {
                this.totals = new long[Math.max(4, this.templates.size() * 2)];
            }
            int[] hits = this.hits;
            long[] totals = this.totals;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Arrays.fill(hits, 0, size, 0);
            for (int i = 0; i < blocks; i++) {
                hits[random.nextInt(size)]++;
            }
            int kinds = this.templates.size();
            Arrays.fill(totals, 0, kinds, 0L);
            for (int s = 0; s < size; s++) {
                int times = hits[s];
                if (times == 0) {
                    continue;
                }
                int[] ids = this.sampleIds.get(s);
                int[] many = this.sampleCounts.get(s);
                for (int k = 0; k < ids.length; k++) {
                    totals[ids[k]] += (long) many[k] * times;
                }
            }
            List<ItemStack> out = new ArrayList<>(4);
            for (int t = 0; t < kinds; t++) {
                long total = totals[t];
                int max = Math.max(1, this.templates.get(t).getMaxStackSize());
                while (total > 0L) {
                    int chunk = (int) Math.min((long) max, total);
                    out.add(this.templates.get(t).copyWithCount(chunk));
                    total -= chunk;
                }
            }
            return out;
        }

        private int templateOf(ItemStack stack) {
            for (int i = 0; i < this.templates.size(); i++) {
                if (ItemStack.isSameItemSameComponents(this.templates.get(i), stack)) {
                    return i;
                }
            }
            this.templates.add(stack.copyWithCount(1));
            return this.templates.size() - 1;
        }
    }

    private static final Map<Key, Model> MODELS = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Model> eldest) {
            return this.size() > MAX_MODELS;
        }
    };

    private DropModels() {
    }

    private static Key key(Block block, ItemStack tool) {
        ItemEnchantments enchantments = tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        return new Key(block, tool.getItem(), enchantments.hashCode());
    }

    public static synchronized Model modelFor(Block block, ItemStack tool) {
        return MODELS.computeIfAbsent(key(block, tool), k -> new Model());
    }

    /** Called with every real result of the break pipeline. */
    public static void feed(Block block, ItemStack tool, List<ItemStack> drops) {
        if (drops != null && !drops.isEmpty()) {
            Model model = modelFor(block, tool);
            model.add(drops);
            if (com.sccadd.SccAddConfig.profile() && model.becameReady()) {
                de.scc.calculator.SccLog.info("sccadd", "dice table ready: "
                        + BuiltInRegistries.BLOCK.getKey(block) + " with "
                        + BuiltInRegistries.ITEM.getKey(tool.getItem()) + " after "
                        + model.learningMillis() + " ms");
            }
        }
    }

    /** Every table the server has learned, for {@code /sccadd breaking tables}. */
    public static synchronized List<TableInfo> snapshot() {
        List<TableInfo> out = new ArrayList<>(MODELS.size());
        for (Map.Entry<Key, Model> entry : MODELS.entrySet()) {
            Model model = entry.getValue();
            out.add(new TableInfo(
                    String.valueOf(BuiltInRegistries.BLOCK.getKey(entry.getKey().block())),
                    String.valueOf(BuiltInRegistries.ITEM.getKey(entry.getKey().tool())),
                    model.samples(), model.ready(), model.learningMillis()));
        }
        out.sort(Comparator.comparing(TableInfo::block).thenComparing(TableInfo::tool));
        return out;
    }

    public static synchronized void clear() {
        MODELS.clear();
    }
}
