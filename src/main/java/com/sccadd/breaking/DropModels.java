package com.sccadd.breaking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.component.DataComponents;
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
 */
public final class DropModels {
    public static final int RING = 256;
    public static final int READY = 48;
    private static final int MAX_MODELS = 512;

    private record Key(Block block, Item tool, int enchantments) {
    }

    public static final class Model {
        private final List<List<ItemStack>> samples = new ArrayList<>(RING);
        private int next;

        private boolean announced;
        private long startedAt = System.currentTimeMillis();

        /** True once, when this table has just collected enough samples to be used. */
        synchronized boolean becameReady() {
            if (!this.announced && this.samples.size() >= READY) {
                this.announced = true;
                return true;
            }
            return false;
        }

        synchronized long learningMillis() {
            return System.currentTimeMillis() - this.startedAt;
        }

        synchronized void add(List<ItemStack> drops) {
            List<ItemStack> copy = new ArrayList<>(drops.size());
            for (ItemStack stack : drops) {
                if (!stack.isEmpty()) {
                    copy.add(stack.copy());
                }
            }
            if (copy.isEmpty()) {
                return;
            }
            if (this.samples.size() < RING) {
                this.samples.add(copy);
            } else {
                this.samples.set(this.next, copy);
                this.next = (this.next + 1) % RING;
            }
        }

        public synchronized boolean ready() {
            return this.samples.size() >= READY;
        }

        /** The summed drops of {@code blocks} simulated breaks, merged into as few stacks as possible. */
        public synchronized List<ItemStack> sum(int blocks) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            Map<Integer, ItemStack> merged = new LinkedHashMap<>();
            List<ItemStack> out = new ArrayList<>(4);
            List<long[]> counts = new ArrayList<>(4);
            for (int i = 0; i < blocks; i++) {
                for (ItemStack drop : this.samples.get(random.nextInt(this.samples.size()))) {
                    int at = -1;
                    for (int s = 0; s < out.size(); s++) {
                        if (ItemStack.isSameItemSameComponents(out.get(s), drop)) {
                            at = s;
                            break;
                        }
                    }
                    if (at < 0) {
                        out.add(drop.copyWithCount(1));
                        counts.add(new long[]{drop.getCount()});
                    } else {
                        counts.get(at)[0] += drop.getCount();
                    }
                }
            }
            List<ItemStack> result = new ArrayList<>(out.size());
            for (int s = 0; s < out.size(); s++) {
                long total = counts.get(s)[0];
                int max = Math.max(1, out.get(s).getMaxStackSize());
                while (total > 0) {
                    int chunk = (int) Math.min(max, total);
                    result.add(out.get(s).copyWithCount(chunk));
                    total -= chunk;
                }
            }
            return result;
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
                        + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block) + " with "
                        + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(tool.getItem()) + " after "
                        + model.learningMillis() + " ms");
            }
        }
    }

    public static synchronized void clear() {
        MODELS.clear();
    }
}
