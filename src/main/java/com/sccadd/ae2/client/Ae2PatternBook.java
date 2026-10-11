package com.sccadd.ae2.client;

import de.scc.calculator.calc.SccRecipe;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.client.screen.CraftSim;
import de.scc.calculator.network.CraftStep;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side twin of {@code Ae2PatternSync}: turns the synced pattern text into
 * real {@link SccRecipe}s so the SCC calculator shows what an AE2 pattern needs.
 */
public final class Ae2PatternBook {
    private static String raw = "";
    private static Map<StackKey, List<SccRecipe>> byOutput = Map.of();

    private Ae2PatternBook() {
    }

    public static synchronized void set(String text) {
        String value = text == null ? "" : text;
        if (value.equals(raw)) {
            return;
        }
        raw = value;
        Map<StackKey, List<SccRecipe>> map = new HashMap<>();
        if (!value.isBlank()) {
            int index = 0;
            for (String line : value.split("\n")) {
                if (!line.startsWith("P\t")) {
                    continue;
                }
                String[] parts = line.split("\t", 4);
                if (parts.length < 4) {
                    continue;
                }
                StackKey output = StackKey.decode(parts[1]);
                if (output == null) {
                    continue;
                }
                List<SccRecipe.Input> inputs = new ArrayList<>(4);
                for (String group : parts[3].split(";")) {
                    int comma = group.indexOf(',');
                    if (comma <= 0) {
                        continue;
                    }
                    int count = Math.max(1, parseInt(group.substring(0, comma), 1));
                    List<StackKey> options = new ArrayList<>(4);
                    for (String option : group.substring(comma + 1).split(",")) {
                        StackKey key = StackKey.decode(option);
                        if (key != null) {
                            options.add(key);
                        }
                    }
                    if (!options.isEmpty()) {
                        inputs.add(new SccRecipe.Input(List.copyOf(options), count, true));
                    }
                }
                if (inputs.isEmpty()) {
                    continue;
                }
                SccRecipe recipe = new SccRecipe(
                        "sccadd:ae2/" + parts[1] + "#" + index++,
                        CraftStep.AE2,
                        "AE2",
                        output,
                        Math.max(1, parseInt(parts[2], 1)),
                        List.copyOf(inputs)
                );
                map.computeIfAbsent(output, key -> new ArrayList<>(2)).add(recipe);
            }
        }
        byOutput = map;
        CraftSim.invalidate();
    }

    public static List<SccRecipe> recipesFor(StackKey item) {
        List<SccRecipe> list = byOutput.get(item);
        return list == null ? List.of() : list;
    }

    public static boolean has(StackKey item) {
        return item != null && byOutput.containsKey(item);
    }

    public static boolean isEmpty() {
        return byOutput.isEmpty();
    }

    public static String raw() {
        return raw;
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception failed) {
            return fallback;
        }
    }
}
