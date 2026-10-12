package com.sccadd.slots;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** The slot rules of a wireless terminal, kept in the item's custom data so they travel with it. */
public final class SlotRules {
    private static final String TAG = "sccadd_slots";

    private SlotRules() {
    }

    public static String encode(List<SlotRule> rules) {
        StringBuilder out = new StringBuilder();
        for (SlotRule rule : rules) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(rule.slot()).append('\t').append(rule.kind().name()).append('\t')
                    .append(clean(rule.item())).append('\t').append(clean(rule.fluid())).append('\t')
                    .append(rule.want()).append('\t').append(rule.craft() ? 1 : 0).append('\t').append(rule.craftAmount());
        }
        return out.toString();
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace('\t', ' ').replace('\n', ' ');
    }

    public static List<SlotRule> decode(String text) {
        List<SlotRule> out = new ArrayList<>();
        boolean[] used = new boolean[SlotRule.SLOT_COUNT];
        if (text == null) {
            return out;
        }
        for (String line : text.split("\n")) {
            String[] parts = line.split("\t", -1);
            if (parts.length < 7) {
                continue;
            }
            try {
                int slot = Integer.parseInt(parts[0]);
                if (slot < 0 || slot >= SlotRule.SLOT_COUNT || used[slot]) {
                    continue;
                }
                SlotRule.Kind kind = SlotRule.Kind.valueOf(parts[1]);
                int want = Math.max(1, Math.min(9999, Integer.parseInt(parts[4])));
                int craftAmount = Math.max(1, Math.min(9999, Integer.parseInt(parts[6])));
                used[slot] = true;
                out.add(new SlotRule(slot, kind, parts[2], parts[3], want, "1".equals(parts[5]), craftAmount));
            } catch (IllegalArgumentException ignored) {
                // skip a broken line
            }
        }
        return out;
    }

    public static List<SlotRule> of(ItemStack terminal) {
        if (terminal == null || terminal.isEmpty()) {
            return List.of();
        }
        CompoundTag data = terminal.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.contains(TAG) ? decode(data.getString(TAG)) : List.of();
    }

    public static void saveTo(ItemStack terminal, List<SlotRule> rules) {
        if (terminal == null || terminal.isEmpty()) {
            return;
        }
        CompoundTag data = terminal.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (rules.isEmpty()) {
            data.remove(TAG);
        } else {
            data.putString(TAG, encode(rules));
        }
        terminal.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }
}
