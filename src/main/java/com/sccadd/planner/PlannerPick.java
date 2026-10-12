package com.sccadd.planner;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * SCC asks a player's client to compute crafting plans (it needs the recipe
 * viewer, which only exists on the client). Its own choice is "the first player
 * in the list" - with two players, an AFK or minimised first player stalls the
 * whole flow for two minutes at a time.
 *
 * <p>This picks better: the player who answered a planning request most recently
 * (their client clearly works), and otherwise rotates through the online players
 * so one silent client cannot starve the flow forever.</p>
 */
public final class PlannerPick {
    private static final long RECENT_TICKS = 12_000L;
    private static final Map<UUID, Long> ANSWERED = new ConcurrentHashMap<>();
    private static final AtomicInteger ROTOR = new AtomicInteger();

    private PlannerPick() {
    }

    public static void answered(ServerPlayer player, long tick) {
        ANSWERED.put(player.getUUID(), tick);
    }

    public static ServerPlayer pick(MinecraftServer server) {
        if (server == null) {
            return null;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return null;
        }
        long now = server.getTickCount();
        ServerPlayer recent = null;
        long best = Long.MIN_VALUE;
        for (ServerPlayer player : players) {
            Long at = ANSWERED.get(player.getUUID());
            if (at != null && now - at <= RECENT_TICKS && at > best) {
                best = at;
                recent = player;
            }
        }
        if (recent != null) {
            return recent;
        }
        return players.get(Math.floorMod(ROTOR.getAndIncrement(), players.size()));
    }
}
