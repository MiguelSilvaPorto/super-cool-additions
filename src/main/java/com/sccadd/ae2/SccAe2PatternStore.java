package com.sccadd.ae2;

import de.scc.calculator.network.NetworkAntennaBlockEntity;
import de.scc.calculator.network.NetworkBlockEntity;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Per-bridge storage for the recipe lines the client sent and the AE2 patterns
 * built from them. Keyed weakly by the bridge block entity (antenna or wireless
 * receiver), so removed blocks do not pile up.
 */
public final class SccAe2PatternStore {
    private static final Map<Object, Entry> ENTRIES = new WeakHashMap<>();

    private SccAe2PatternStore() {
    }

    private static final class Entry {
        List<SccAe2Recipe> recipes = List.of();
        @Nullable
        List<appeng.api.crafting.IPatternDetails> patterns;
    }

    public static synchronized void setRecipes(Object be, List<SccAe2Recipe> recipes) {
        Entry entry = ENTRIES.computeIfAbsent(be, key -> new Entry());
        if (entry.recipes.equals(recipes)) {
            return;
        }
        entry.recipes = recipes;
        entry.patterns = null;
    }

    public static synchronized List<SccAe2Recipe> recipes(Object be) {
        Entry entry = ENTRIES.get(be);
        return entry == null ? List.of() : entry.recipes;
    }

    public static synchronized List<appeng.api.crafting.IPatternDetails> patterns(Object be) {
        Entry entry = ENTRIES.get(be);
        if (entry == null || entry.recipes.isEmpty()) {
            return List.of();
        }
        if (entry.patterns == null) {
            entry.patterns = SccAe2Patterns.build(entry.recipes);
        }
        return entry.patterns;
    }

    public static synchronized void clear(Object be) {
        ENTRIES.remove(be);
    }

    /** Compatibility helper for callers that still hold an antenna reference. */
    public static List<appeng.api.crafting.IPatternDetails> patterns(NetworkAntennaBlockEntity be) {
        return patterns((Object) be);
    }

    /** Compatibility helper for callers that still hold a network block entity. */
    public static List<SccAe2Recipe> recipes(NetworkBlockEntity be) {
        return recipes((Object) be);
    }
}
