package com.sccadd.ae2;

import appeng.api.crafting.IPatternDetails;
import de.scc.calculator.network.NetworkAntennaBlockEntity;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Per-antenna storage for the recipe lines the client sent and the AE2 patterns
 * built from them. Keyed by the antenna block entity, weakly, so removed
 * antennas do not pile up.
 */
public final class SccAe2PatternStore {
    private static final Map<NetworkAntennaBlockEntity, Entry> ENTRIES = new WeakHashMap<>();

    private SccAe2PatternStore() {
    }

    private static final class Entry {
        List<SccAe2Recipe> recipes = List.of();
        @Nullable
        List<IPatternDetails> patterns;
    }

    public static synchronized void setRecipes(NetworkAntennaBlockEntity be, List<SccAe2Recipe> recipes) {
        Entry entry = ENTRIES.computeIfAbsent(be, key -> new Entry());
        if (entry.recipes.equals(recipes)) {
            return;
        }
        entry.recipes = recipes;
        entry.patterns = null;
    }

    public static synchronized List<SccAe2Recipe> recipes(NetworkAntennaBlockEntity be) {
        Entry entry = ENTRIES.get(be);
        return entry == null ? List.of() : entry.recipes;
    }

    public static synchronized List<IPatternDetails> patterns(NetworkAntennaBlockEntity be) {
        Entry entry = ENTRIES.get(be);
        if (entry == null || entry.recipes.isEmpty()) {
            return List.of();
        }
        if (entry.patterns == null) {
            entry.patterns = SccAe2Patterns.build(entry.recipes);
        }
        return entry.patterns;
    }

    public static synchronized void clear(NetworkAntennaBlockEntity be) {
        ENTRIES.remove(be);
    }
}
