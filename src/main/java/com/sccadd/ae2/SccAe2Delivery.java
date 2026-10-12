package com.sccadd.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import de.scc.calculator.SccLog;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.compat.BridgeGuard;
import de.scc.calculator.network.NetworkBlockEntity;
import com.sccadd.ae2.receiver.ReceiverMeConnector;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.StorageConnectorBlockEntity;
import de.scc.calculator.network.TerminalBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Hands finished SCC items to a waiting AE2 job: takes them from the SCC network
 * and inserts them into the grid's ME storage.
 */
public final class SccAe2Delivery {
    private static final IActionSource SOURCE = IActionSource.empty();

    private SccAe2Delivery() {
    }

    public static long deliver(ServerLevel level, MEStorage inventory, NetworkView view, NetworkBlockEntity be,
                               AEItemKey key, StackKey wanted, long want) {
        if (view == null || ownFree(view, wanted) <= 0L) {
            return 0L;
        }
        com.sccadd.ae2.receiver.ReceiverMeConnector.suspend(true);
        try {
            return deliverNow(level, inventory, view, be, key, wanted, want);
        } finally {
            com.sccadd.ae2.receiver.ReceiverMeConnector.suspend(false);
        }
    }

    /** Storages that really belong to SCC - not the ME network the items are being delivered to. */
    private static java.util.List<StorageConnectorBlockEntity> ownStorages(NetworkView view) {
        java.util.List<StorageConnectorBlockEntity> out = new java.util.ArrayList<>();
        for (StorageConnectorBlockEntity storage : view.takeOrder()) {
            if (storage instanceof ReceiverMeConnector || storage.ae2Target() != null) {
                continue;
            }
            out.add(storage);
        }
        return out;
    }

    private static long ownFree(NetworkView view, StackKey key) {
        long have = 0L;
        for (StorageConnectorBlockEntity storage : ownStorages(view)) {
            have += storage.available(key, Integer.MAX_VALUE);
        }
        return Math.max(0L, have - view.reservedOf(key));
    }

    /** Takes from SCC's own storages only: pulling from the ME and putting it back would deliver nothing. */
    private static ItemStack takeOwn(NetworkView view, StackKey key, int amount) {
        ItemStack result = ItemStack.EMPTY;
        int remaining = amount;
        for (StorageConnectorBlockEntity storage : ownStorages(view)) {
            if (remaining <= 0) {
                break;
            }
            ItemStack taken = storage.extract(key, remaining, false);
            if (taken.isEmpty()) {
                continue;
            }
            if (result.isEmpty()) {
                result = taken.copy();
            } else if (ItemStack.isSameItemSameComponents(result, taken)) {
                result.grow(taken.getCount());
            } else {
                view.giveBack(taken, null, null);
                continue;
            }
            remaining -= taken.getCount();
        }
        if (!result.isEmpty()) {
            view.markStale();
        }
        return result;
    }

    private static long deliverNow(ServerLevel level, MEStorage inventory, NetworkView view, NetworkBlockEntity be,
                                   AEItemKey key, StackKey wanted, long want) {
        ItemStack template = key.toStack();
        int max = Math.max(1, template.getMaxStackSize());
        long done = 0L;
        while (done < want) {
            int batch = (int) Math.min((long) max, want - done);
            BridgeGuard.enter();
            long fits;
            try {
                fits = inventory.insert(key, batch, Actionable.SIMULATE, SOURCE);
            } finally {
                BridgeGuard.leave();
            }
            if (fits <= 0L) {
                break;
            }
            ItemStack got = takeOwn(view, wanted, (int) Math.min((long) batch, Math.min(fits, ownFree(view, wanted))));
            if (got.isEmpty()) {
                break;
            }
            if (!ItemStack.isSameItemSameComponents(got, template)) {
                view.giveBack(got, level, be.getBlockPos());
                break;
            }
            BridgeGuard.enter();
            long in;
            try {
                in = inventory.insert(key, got.getCount(), Actionable.MODULATE, SOURCE);
            } finally {
                BridgeGuard.leave();
            }
            if (in < got.getCount()) {
                view.giveBack(got.copyWithCount(got.getCount() - (int) in), level, be.getBlockPos());
            }
            done += in;
            if (in < batch) {
                break;
            }
        }
        if (done > 0L) {
            TerminalBlockEntity terminal = view.terminalNear(level);
            if (terminal != null) {
                terminal.requestRefresh();
            }
            SccLog.info("sccadd", "delivered " + done + "x " + key.getDisplayName().getString() + " to the waiting AE2 job");
        }
        return done;
    }
}
