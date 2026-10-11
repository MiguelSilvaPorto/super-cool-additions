package com.sccadd.ae2;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client-side text of the AE2 patterns per terminal, mirrored out of the
 * terminal mixin so other mixins can read it without casting to a mixin class
 * (mixin rejects casts between mixins of different targets).
 */
public final class Ae2PatternCache {
    private static final Map<Object, String> TEXT = Collections.synchronizedMap(new WeakHashMap<>());

    private Ae2PatternCache() {
    }

    public static void put(Object terminal, String text) {
        if (text == null || text.isEmpty()) {
            TEXT.remove(terminal);
        } else {
            TEXT.put(terminal, text);
        }
    }

    public static String get(Object terminal) {
        String text = TEXT.get(terminal);
        return text == null ? "" : text;
    }
}
