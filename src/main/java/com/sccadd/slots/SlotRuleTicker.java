package com.sccadd.slots;

import de.scc.calculator.SccLog;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.CraftOrders;
import de.scc.calculator.network.NetworkAntennaBlockEntity;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.SccSecurity;
import de.scc.calculator.network.WirelessAccess;
import de.scc.calculator.network.WirelessTerminalItem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * Applies the slot rules of a player's wireless terminal once a second: export rules
 * keep a slot stocked from the system (ordering more when it runs dry), import rules
 * send a slot's items and/or the fluid inside its bucket or tank to the system.
 */
@EventBusSubscriber(modid = "sccadd")
public final class SlotRuleTicker {
    private static final int EVERY = 20;
    private static final int OPS_PER_PASS = 6;
    private static final int FLUID_PER_PASS = 1000;
    private static final long CRAFT_COOLDOWN = 200L;
    private static final Map<String, Long> LAST_CRAFT = new HashMap<>();

    private SlotRuleTicker() {
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || (player.tickCount + player.getId()) % EVERY != 0) {
            return;
        }
        ItemStack terminal = WirelessAccess.findAny(player);
        if (terminal.isEmpty()) {
            return;
        }
        List<SlotRule> rules = SlotRules.of(terminal);
        if (rules.isEmpty()) {
            return;
        }
        NetworkAntennaBlockEntity antenna = WirelessAccess.antenna(player, terminal);
        if (antenna == null || !WirelessAccess.inRange(player, antenna, WirelessAccess.linkedDim(terminal))) {
            return;
        }
        NetworkView view = antenna.network();
        if (view == null || !view.online()) {
            return;
        }
        boolean mayTake = SccSecurity.may(player, view, SccSecurity.Right.TAKE);
        boolean mayPut = SccSecurity.may(player, view, SccSecurity.Right.PUT);
        int ops = 0;
        try {
            for (SlotRule rule : rules) {
                if (ops >= OPS_PER_PASS) {
                    break;
                }
                if (rule.kind() == SlotRule.Kind.EXPORT) {
                    if (mayTake) {
                        ops += export(player, antenna, view, rule);
                    }
                } else if (mayPut) {
                    ops += importSlot(player, view, rule);
                }
            }
        } catch (Throwable failed) {
            SccLog.warn("sccadd", "slot rules failed: " + failed);
        }
    }

    private static int export(ServerPlayer player, NetworkAntennaBlockEntity antenna, NetworkView view, SlotRule rule) {
        Inventory inventory = player.getInventory();
        int done = 0;
        if (rule.hasItem()) {
            StackKey key = StackKey.decode(rule.item());
            if (key != null) {
                ItemStack current = inventory.getItem(rule.slot());
                if (current.isEmpty() || key.matches(current)) {
                    int want = Math.max(1, Math.min(rule.want(), key.display().getMaxStackSize()));
                    int have = current.isEmpty() ? 0 : current.getCount();
                    if (have < want) {
                        ItemStack got = view.extractFree(key, want - have, false);
                        if (!got.isEmpty()) {
                            if (current.isEmpty()) {
                                inventory.setItem(rule.slot(), got);
                            } else {
                                current.grow(got.getCount());
                            }
                            done++;
                        } else if (rule.craft()) {
                            orderMore(player, antenna, key, rule);
                        }
                    }
                }
            }
        }
        if (rule.hasFluid()) {
            done += fillContainer(inventory, view, rule);
        }
        return done;
    }

    private static void orderMore(ServerPlayer player, NetworkAntennaBlockEntity antenna, StackKey key, SlotRule rule) {
        long now = player.level().getGameTime();
        String id = player.getUUID() + "/" + rule.slot();
        Long last = LAST_CRAFT.get(id);
        if (last != null && now - last < CRAFT_COOLDOWN) {
            return;
        }
        LAST_CRAFT.put(id, now);
        try {
            CraftOrders.order(player.containerMenu, player, antenna, key, rule.craftAmount());
        } catch (Throwable failed) {
            SccLog.warn("sccadd", "slot rule craft order failed: " + failed);
        }
    }

    private static Fluid fluidOf(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null || !BuiltInRegistries.FLUID.containsKey(location) ? Fluids.EMPTY : BuiltInRegistries.FLUID.get(location);
    }

    /** Keeps the bucket or tank in the slot full of the rule's fluid, taking it from the system. */
    private static int fillContainer(Inventory inventory, NetworkView view, SlotRule rule) {
        Fluid fluid = fluidOf(rule.fluid());
        ItemStack stack = inventory.getItem(rule.slot());
        if (fluid == Fluids.EMPTY || stack.isEmpty() || stack.getCount() != 1) {
            return 0;
        }
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null || handler.getTanks() < 1) {
            return 0;
        }
        FluidStack inside = handler.getFluidInTank(0);
        if (!inside.isEmpty() && inside.getFluid() != fluid) {
            return 0;
        }
        int room = handler.getTankCapacity(0) - inside.getAmount();
        if (room <= 0) {
            return 0;
        }
        int available = view.drainFluid(fluid, Math.min(room, FLUID_PER_PASS), true);
        if (available <= 0) {
            return 0;
        }
        int accepted = handler.fill(new FluidStack(fluid, available), IFluidHandler.FluidAction.EXECUTE);
        if (accepted <= 0) {
            return 0;
        }
        view.drainFluid(fluid, accepted, false);
        inventory.setItem(rule.slot(), handler.getContainer());
        return 1;
    }

    private static int importSlot(ServerPlayer player, NetworkView view, SlotRule rule) {
        Inventory inventory = player.getInventory();
        ItemStack stack = inventory.getItem(rule.slot());
        if (stack.isEmpty() || stack.getItem() instanceof WirelessTerminalItem) {
            return 0;
        }
        int done = 0;
        if (rule.kind() == SlotRule.Kind.IMPORT_FLUID || rule.kind() == SlotRule.Kind.IMPORT_BOTH) {
            done += drainContainer(inventory, view, rule.slot());
            stack = inventory.getItem(rule.slot());
        }
        if ((rule.kind() == SlotRule.Kind.IMPORT_ITEM || rule.kind() == SlotRule.Kind.IMPORT_BOTH) && !stack.isEmpty()) {
            ItemStack rest = view.insert(stack.copy(), false);
            if (rest.getCount() != stack.getCount()) {
                inventory.setItem(rule.slot(), rest.isEmpty() ? ItemStack.EMPTY : rest);
                done++;
            }
        }
        return done;
    }

    /** Pours the fluid of a bucket or tank into the system's fluid storage; the container stays in the slot. */
    private static int drainContainer(Inventory inventory, NetworkView view, int slot) {
        ItemStack stack = inventory.getItem(slot);
        if (stack.getCount() != 1) {
            return 0;
        }
        IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) {
            return 0;
        }
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack inside = handler.getFluidInTank(tank);
            if (inside.isEmpty()) {
                continue;
            }
            FluidStack offered = handler.drain(inside.copy(), IFluidHandler.FluidAction.SIMULATE);
            if (offered.isEmpty()) {
                continue;
            }
            FluidStack rest = view.insertFluid(offered.copy(), true);
            int accepted = offered.getAmount() - rest.getAmount();
            if (accepted <= 0) {
                continue;
            }
            FluidStack taken = handler.drain(new FluidStack(inside.getFluid(), accepted), IFluidHandler.FluidAction.EXECUTE);
            if (!taken.isEmpty()) {
                view.insertFluid(taken, false);
                inventory.setItem(slot, handler.getContainer());
                return 1;
            }
        }
        return 0;
    }
}
