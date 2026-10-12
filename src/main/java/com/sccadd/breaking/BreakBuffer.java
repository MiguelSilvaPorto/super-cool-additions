package com.sccadd.breaking;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The hidden stock of a Breaking Calculator: results of simulated breaks that did not fit into the
 * inventory above it yet. Its size depends on the calculator's tier; when it is full the calculator
 * stops for lack of space.
 */
public final class BreakBuffer {
    private static final String TAG = "sccadd_buffer";

    private final List<ItemStack> stacks = new ArrayList<>();
    private long total;

    public boolean isEmpty() {
        return this.stacks.isEmpty();
    }

    public long total() {
        return this.total;
    }

    public long free(long capacity) {
        return Math.max(0L, capacity - this.total);
    }

    public void add(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        this.total += stack.getCount();
        int max = Math.max(1, stack.getMaxStackSize());
        for (ItemStack held : this.stacks) {
            if (held.getCount() < max && ItemStack.isSameItemSameComponents(held, stack)) {
                int moved = Math.min(max - held.getCount(), stack.getCount());
                held.grow(moved);
                stack = stack.copyWithCount(stack.getCount() - moved);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }
        this.stacks.add(stack.copy());
    }

    /** Pushes what fits into the inventory above; returns true when it is empty afterwards. */
    public boolean flushInto(IItemHandler out) {
        for (int i = 0; i < this.stacks.size(); i++) {
            ItemStack held = this.stacks.get(i);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(out, held.copy(), false);
            int moved = held.getCount() - rest.getCount();
            if (moved > 0) {
                this.total -= moved;
                held.shrink(moved);
            }
            if (held.isEmpty()) {
                this.stacks.remove(i--);
            } else if (moved == 0) {
                // The inventory refuses this item; try the others.
                continue;
            }
        }
        return this.stacks.isEmpty();
    }

    public void dropAll(Level level, BlockPos pos) {
        for (ItemStack stack : this.stacks) {
            Block.popResource(level, pos, stack.copy());
        }
        this.stacks.clear();
        this.total = 0L;
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (this.stacks.isEmpty()) {
            tag.remove(TAG);
            return;
        }
        ListTag list = new ListTag();
        for (ItemStack stack : this.stacks) {
            CompoundTag entry = new CompoundTag();
            entry.put("stack", stack.copyWithCount(1).save(registries));
            entry.putInt("n", stack.getCount());
            list.add(entry);
        }
        tag.put(TAG, list);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        this.stacks.clear();
        this.total = 0L;
        if (!tag.contains(TAG, Tag.TAG_LIST)) {
            return;
        }
        for (Tag raw : tag.getList(TAG, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            ItemStack.parse(registries, entry.get("stack")).ifPresent(stack -> {
                int count = Math.max(1, entry.getInt("n"));
                if (!stack.isEmpty()) {
                    this.stacks.add(stack.copyWithCount(count));
                    this.total += count;
                }
            });
        }
    }
}
