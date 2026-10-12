package com.sccadd.slots;

import com.sccadd.SccAddConfig;
import de.scc.calculator.SccLog;
import de.scc.calculator.network.ItemFilter;
import de.scc.calculator.network.NetworkAntennaBlockEntity;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.RestockConfig;
import de.scc.calculator.network.SccSecurity;
import de.scc.calculator.network.WirelessAccess;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * SCC's magnet only takes an item once it touches the player. This makes it reach: dropped items
 * within {@code [magnet] range} blocks (16 by default) go straight into the SCC network, honouring
 * the magnet's whitelist/blacklist, the network's permissions and the item's owner/pickup delay.
 */
@EventBusSubscriber(modid = "sccadd")
public final class MagnetReach {
    private static final int EVERY = 5;
    private static final int LOOKUP_TICKS = 10;
    private static final int MAX_PER_PASS = 48;
    private static final Map<UUID, State> STATE = new HashMap<>();

    private record State(long tick, @Nullable NetworkAntennaBlockEntity antenna, @Nullable ItemFilter filter) {
    }

    private MagnetReach() {
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || (player.tickCount + player.getId()) % EVERY != 0) {
            return;
        }
        int range = SccAddConfig.magnetRange();
        if (range <= 0 || player.isSpectator() || !player.isAlive()) {
            return;
        }
        try {
            State state = state(player);
            NetworkAntennaBlockEntity antenna = state.antenna();
            if (antenna == null || antenna.isRemoved()) {
                return;
            }
            NetworkView view = antenna.network();
            if (view == null || !view.online() || view.storages().isEmpty()) {
                return;
            }
            AABB box = player.getBoundingBox().inflate(range);
            List<ItemEntity> drops = player.level().getEntitiesOfClass(ItemEntity.class, box,
                    entity -> !entity.isRemoved() && !entity.hasPickUpDelay() && entity.distanceToSqr(player) <= (double) range * range);
            int handled = 0;
            for (ItemEntity entity : drops) {
                if (handled >= MAX_PER_PASS) {
                    break;
                }
                if (take(player, view, state.filter(), entity)) {
                    handled++;
                }
            }
        } catch (Throwable failed) {
            SccLog.warn("sccadd", "magnet reach failed: " + failed);
        }
    }

    private static boolean take(ServerPlayer player, NetworkView view, @Nullable ItemFilter filter, ItemEntity entity) {
        if (entity.getTarget() != null && !entity.getTarget().equals(player.getUUID())) {
            return false;
        }
        Entity thrower = entity.getOwner();
        if (thrower != null && thrower.getUUID().equals(player.getUUID())) {
            return false; // what you threw away yourself stays thrown
        }
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || filter != null && !filter.accepts(stack)) {
            return false;
        }
        ItemStack rest = view.insertPickup(stack.copy(), false);
        int moved = stack.getCount() - rest.getCount();
        if (moved <= 0) {
            return false;
        }
        Item item = stack.getItem();
        if (rest.isEmpty()) {
            player.take(entity, moved);
            entity.discard();
        } else {
            stack.setCount(rest.getCount());
        }
        player.awardStat(Stats.ITEM_PICKED_UP.get(item), moved);
        return true;
    }

    private static State state(ServerPlayer player) {
        long now = player.level().getGameTime();
        State known = STATE.get(player.getUUID());
        if (known != null && now - known.tick() >= 0L && now - known.tick() < LOOKUP_TICKS) {
            return known;
        }
        State fresh = lookup(player, now);
        STATE.put(player.getUUID(), fresh);
        return fresh;
    }

    private static State lookup(ServerPlayer player, long now) {
        ItemStack terminal = WirelessAccess.find(player, stack -> RestockConfig.of(stack).magnet());
        if (terminal.isEmpty()) {
            return new State(now, null, null);
        }
        RestockConfig config = RestockConfig.of(terminal);
        NetworkAntennaBlockEntity antenna = WirelessAccess.antenna(player, terminal, false);
        if (antenna == null || !WirelessAccess.inRange(player, antenna, WirelessAccess.linkedDim(terminal))) {
            return new State(now, null, null);
        }
        NetworkView view = antenna.network();
        if (view == null || !view.online() || view.storages().isEmpty()
                || !SccSecurity.may(player, view, SccSecurity.Right.PUT)) {
            return new State(now, null, null);
        }
        ItemFilter filter = null;
        if (!config.magnetFilter().isEmpty() || config.magnetBlacklist()) {
            filter = new ItemFilter();
            for (int i = 0; i < config.magnetFilter().size() && i < 9; i++) {
                filter.set(i, config.magnetFilter().get(i));
            }
            filter.setBlacklist(config.magnetBlacklist());
        }
        return new State(now, antenna, filter);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATE.remove(event.getEntity().getUUID());
    }
}
