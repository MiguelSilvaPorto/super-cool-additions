package com.sccadd.ae2;

import de.scc.calculator.calc.StackKey;
import java.util.ArrayList;
import java.util.List;

/**
 * One SCC recipe in the shape the server needs it for an AE2 pattern:
 * output, output count and the ingredient groups (each with alternatives).
 */
public record SccAe2Recipe(StackKey output, int outputCount, List<Input> inputs) {
    public record Input(int count, List<StackKey> options) {
    }

    /** Parses the {@code P\t...} lines of the offer answer; ignores everything else. */
    public static List<SccAe2Recipe> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<SccAe2Recipe> out = new ArrayList<>();
        for (String line : text.split("\n")) {
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
            List<Input> inputs = new ArrayList<>(4);
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
                    inputs.add(new Input(count, List.copyOf(options)));
                }
            }
            if (!inputs.isEmpty()) {
                out.add(new SccAe2Recipe(output, Math.max(1, parseInt(parts[2], 1)), List.copyOf(inputs)));
            }
        }
        return List.copyOf(out);
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception failed) {
            return fallback;
        }
    }
}
