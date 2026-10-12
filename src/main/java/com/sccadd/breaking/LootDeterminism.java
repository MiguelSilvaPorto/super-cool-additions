package com.sccadd.breaking;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;

/**
 * Decides, conservatively, whether a loot table always gives the same drops for the same block
 * and tool. Only tables built entirely from known deterministic parts qualify (one item per
 * pool, constant rolls, conditions that only look at the block state and the tool); anything
 * unknown - including every modded entry, fortune bonus or random chance - is treated as random,
 * so the cache below can never change what a block drops.
 */
public final class LootDeterminism {
    private static final Set<String> CONDITIONS = Set.of(
            "minecraft:survives_explosion", "minecraft:match_tool", "minecraft:block_state_property");
    private static final Set<String> FUNCTIONS = Set.of(
            "minecraft:explosion_decay", "minecraft:copy_state", "minecraft:copy_components",
            "minecraft:set_components", "minecraft:set_name", "minecraft:set_lore", "minecraft:set_item");
    private static final Map<LootTable, Boolean> VERDICTS = new WeakHashMap<>();
    private static final Map<Class<?>, Map<String, Field>> FIELDS = new IdentityHashMap<>();

    private LootDeterminism() {
    }

    public static synchronized boolean isDeterministic(LootTable table) {
        Boolean known = VERDICTS.get(table);
        if (known == null) {
            boolean verdict;
            try {
                verdict = table(table);
            } catch (Throwable failed) {
                verdict = false;
            }
            VERDICTS.put(table, verdict);
            known = verdict;
        }
        return known;
    }

    private static boolean table(LootTable table) throws Exception {
        List<?> pools = (List<?>) read(table, "pools");
        if (pools == null || pools.isEmpty() || !functions((List<?>) read(table, "functions"))) {
            return false;
        }
        for (Object pool : pools) {
            if (!pool((LootPool) pool)) {
                return false;
            }
        }
        return true;
    }

    private static boolean pool(LootPool pool) throws Exception {
        if (!constant((NumberProvider) read(pool, "rolls"), -1.0F)
                || !constant((NumberProvider) read(pool, "bonusRolls"), 0.0F)) {
            return false;
        }
        List<?> entries = (List<?>) read(pool, "entries");
        if (entries == null || entries.size() != 1) {
            return false; // several entries are picked by weight: random
        }
        return conditions((List<?>) read(pool, "conditions"))
                && functions((List<?>) read(pool, "functions"))
                && entry((LootPoolEntryContainer) entries.get(0));
    }

    private static boolean entry(LootPoolEntryContainer entry) throws Exception {
        ResourceLocation type = BuiltInRegistries.LOOT_POOL_ENTRY_TYPE.getKey(entry.getType());
        if (type == null || !conditions((List<?>) read(entry, "conditions"))) {
            return false;
        }
        switch (type.toString()) {
            case "minecraft:empty":
                return true;
            case "minecraft:item":
                return functions((List<?>) read(entry, "functions"));
            case "minecraft:alternatives":
                List<?> children = (List<?>) read(entry, "children");
                if (children == null || children.isEmpty()) {
                    return false;
                }
                for (Object child : children) {
                    if (!entry((LootPoolEntryContainer) child)) {
                        return false;
                    }
                }
                return true;
            default:
                return false;
        }
    }

    private static boolean conditions(List<?> list) {
        if (list == null) {
            return false;
        }
        for (Object condition : list) {
            ResourceLocation type = BuiltInRegistries.LOOT_CONDITION_TYPE.getKey(((LootItemCondition) condition).getType());
            if (type == null || !CONDITIONS.contains(type.toString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean functions(List<?> list) throws Exception {
        if (list == null) {
            return false;
        }
        for (Object function : list) {
            LootItemFunction fn = (LootItemFunction) function;
            ResourceLocation type = BuiltInRegistries.LOOT_FUNCTION_TYPE.getKey(fn.getType());
            if (type == null) {
                return false;
            }
            String id = type.toString();
            if (id.equals("minecraft:set_count")) {
                if (!constantAny((NumberProvider) read(fn, "value"))
                        || !conditions((List<?>) read(fn, "predicates"))) {
                    return false;
                }
            } else if (!FUNCTIONS.contains(id) || !conditions((List<?>) read(fn, "predicates"))) {
                return false;
            }
        }
        return true;
    }

    private static boolean constantAny(NumberProvider provider) {
        return provider instanceof ConstantValue;
    }

    private static boolean constant(NumberProvider provider, float expected) {
        if (!(provider instanceof ConstantValue constant)) {
            return false;
        }
        return expected < 0.0F || constant.value() == expected;
    }

    private static Object read(Object owner, String name) throws Exception {
        Class<?> type = owner.getClass();
        Field field = null;
        Map<String, Field> cache = FIELDS.computeIfAbsent(type, key -> new java.util.HashMap<>());
        if (cache.containsKey(name)) {
            field = cache.get(name);
        } else {
            for (Class<?> c = type; c != null && field == null; c = c.getSuperclass()) {
                try {
                    field = c.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    // look in the parent class
                }
            }
            if (field != null) {
                field.setAccessible(true);
            }
            cache.put(name, field);
        }
        if (field == null) {
            throw new NoSuchFieldException(name);
        }
        return field.get(owner);
    }
}
