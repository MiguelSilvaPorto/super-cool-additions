package com.sccadd.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.compat.BridgeGuard;
import de.scc.calculator.network.NetworkBlockEntity;
import de.scc.calculator.network.NetworkView;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The SCC network seen as AE2 storage: what an ME terminal or a Storage Bus
 * shows when it looks at an SCC bridge block (wireless receiver).
 */
public final class SccMeStorage implements MEStorage {
    private final NetworkBlockEntity be;

    public SccMeStorage(NetworkBlockEntity be) {
        this.be = be;
    }

    @Override
    public Component getDescription() {
        return Component.translatable("scc.ae2.storage");
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (BridgeGuard.busy()) {
            return;
        }
        NetworkView view = this.be.network();
        if (view == null) {
            return;
        }
        BridgeGuard.enter();
        try {
            for (Map.Entry<StackKey, Long> entry : view.contents().entrySet()) {
                Long amount = entry.getValue();
                if (amount == null || amount <= 0L) {
                    continue;
                }
                AEItemKey key = AEItemKey.of(entry.getKey().display());
                if (key != null) {
                    out.add(key, amount);
                }
            }
        } catch (Throwable failed) {
            // never let a storage query break the grid
        } finally {
            BridgeGuard.leave();
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (BridgeGuard.busy() || !(what instanceof AEItemKey item) || amount <= 0L) {
            return 0L;
        }
        NetworkView view = this.be.network();
        if (view == null) {
            return 0L;
        }
        BridgeGuard.enter();
        try {
            long done = 0L;
            int max = Math.max(1, item.getItem().getDefaultMaxStackSize());
            while (done < amount) {
                int batch = (int) Math.min((long) max, amount - done);
                ItemStack stack = item.toStack(batch);
                ItemStack rest = view.insert(stack, mode == Actionable.SIMULATE);
                done += batch - rest.getCount();
                if (!rest.isEmpty()) {
                    break;
                }
            }
            return done;
        } catch (Throwable failed) {
            return 0L;
        } finally {
            BridgeGuard.leave();
        }
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (BridgeGuard.busy() || !(what instanceof AEItemKey item) || amount <= 0L) {
            return 0L;
        }
        NetworkView view = this.be.network();
        if (view == null) {
            return 0L;
        }
        StackKey key = StackKey.of(item.toStack(1));
        if (key == null) {
            return 0L;
        }
        BridgeGuard.enter();
        try {
            if (mode == Actionable.SIMULATE) {
                return Math.min(amount, view.free(key));
            }
            long done = 0L;
            int max = Math.max(1, item.getItem().getDefaultMaxStackSize());
            ItemStack wanted = item.toStack(1);
            while (done < amount) {
                int batch = (int) Math.min((long) max, amount - done);
                ItemStack got = view.extractFree(key, batch, false);
                if (got.isEmpty()) {
                    break;
                }
                if (!ItemStack.isSameItemSameComponents(got, wanted)) {
                    view.giveBack(got, this.be.getLevel(), this.be.getBlockPos());
                    break;
                }
                done += got.getCount();
                if (got.getCount() < batch) {
                    break;
                }
            }
            return done;
        } catch (Throwable failed) {
            return 0L;
        } finally {
            BridgeGuard.leave();
        }
    }

    /** Small helper for the delivery loop: how much of the key sits in the network. */
    public static long free(NetworkView view, StackKey key) {
        return view == null ? 0L : view.free(key);
    }
}
