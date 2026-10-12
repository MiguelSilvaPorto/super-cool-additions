package com.sccadd.net;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Reassembles the chunked recipe text sent with an offer answer. No size ceiling:
 * as many chunks as the pack needs, each one small enough for the connection.
 * Incomplete assemblies are dropped after a while so nothing leaks.
 */
public final class SccPatternChunks {
    private static final long TTL_MS = 60_000L;
    private static final Map<Key, Assembly> PENDING = new HashMap<>();

    private SccPatternChunks() {
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final class Assembly {
        final String[] parts;
        int received;
        final long startedAt = System.currentTimeMillis();

        Assembly(int count) {
            this.parts = new String[count];
        }
    }

    public static synchronized void accept(ServerPlayer player, SccPatternsPayload payload) {
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(entry -> now - entry.getValue().startedAt > TTL_MS);

        int count = Math.max(1, Math.min(4_096, payload.count()));
        int index = Math.max(0, Math.min(count - 1, payload.index()));
        Key key = new Key(player.serverLevel().dimension(), payload.pos().immutable());
        Assembly assembly = PENDING.get(key);
        if (assembly == null || assembly.parts.length != count) {
            assembly = new Assembly(count);
            PENDING.put(key, assembly);
        }
        if (assembly.parts[index] == null) {
            assembly.parts[index] = payload.chunk() == null ? "" : payload.chunk();
            assembly.received++;
        }
        if (assembly.received >= count) {
            PENDING.remove(key);
            StringBuilder text = new StringBuilder();
            for (String part : assembly.parts) {
                if (part != null) {
                    text.append(part);
                }
            }
            SccPatternsPayload.store(player, payload, text.toString());
        }
    }
}
