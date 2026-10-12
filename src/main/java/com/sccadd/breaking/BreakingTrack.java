package com.sccadd.breaking;

import de.scc.calculator.network.BreakingCalculatorBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * The Breaking Calculators that are loaded right now, so commands can report and poke them. Entries
 * are refreshed by the calculators' own tick and forgotten after a few seconds without one (chunk
 * unloaded), so nothing keeps a removed machine alive.
 */
public final class BreakingTrack {
    private static final long FRESH_NANOS = 3_000_000_000L;

    private record Seen(ServerLevel level, long at) {
    }

    /** One loaded calculator and the level it lives in. */
    public record Live(BreakingCalculatorBlockEntity be, ServerLevel level) {
    }

    private static final Map<BreakingCalculatorBlockEntity, Seen> SEEN = new WeakHashMap<>();

    private BreakingTrack() {
    }

    public static void seen(BreakingCalculatorBlockEntity be, ServerLevel level) {
        SEEN.put(be, new Seen(level, System.nanoTime()));
    }

    public static List<Live> loaded() {
        long now = System.nanoTime();
        SEEN.entrySet().removeIf(entry -> now - entry.getValue().at() > FRESH_NANOS || entry.getKey().isRemoved());
        List<Live> out = new ArrayList<>(SEEN.size());
        for (Map.Entry<BreakingCalculatorBlockEntity, Seen> entry : SEEN.entrySet()) {
            out.add(new Live(entry.getKey(), entry.getValue().level()));
        }
        return out;
    }
}
