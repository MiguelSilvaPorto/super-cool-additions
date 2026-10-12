package com.sccadd.ae2.receiver;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import com.sccadd.mixin.Ae2MeStoreInvoker;
import de.scc.calculator.compat.ae2.Ae2MeStore;
import de.scc.calculator.network.SccRegistry;
import de.scc.calculator.network.StorageConnectorBlockEntity;
import de.scc.calculator.network.WirelessReceiverBlockEntity;
import java.util.List;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A storage connector that is not a block: the wireless receiver's own view of the
 * AE2 grid it touches, joined to the SCC network at scan time so the ME items
 * count as SCC stock - the other half of the bridge (SCC -> ME is the mount).
 */
public final class ReceiverMeConnector extends StorageConnectorBlockEntity {
    /** Set while SCC items are handed to AE2, so the ME is not read back into itself. */
    private static boolean suspended;

    private final ReceiverBridge bridge;
    @Nullable
    private IGrid cachedGrid;
    @Nullable
    private Ae2MeStore cachedStore;

    ReceiverMeConnector(WirelessReceiverBlockEntity receiver, ReceiverBridge bridge) {
        super(receiver.getBlockPos(), SccRegistry.STORAGE_CONNECTOR.get().defaultBlockState());
        this.bridge = bridge;
        this.setLevel(receiver.getLevel());
    }

    public static void suspend(boolean value) {
        suspended = value;
    }

    @Nullable
    private IGrid grid() {
        IGridNode node = this.bridge.node();
        return node != null && node.isActive() ? node.getGrid() : null;
    }

    @Nullable
    @Override
    public IItemHandler handler() {
        if (suspended || this.level == null) {
            return null;
        }
        IGrid grid = this.grid();
        if (grid == null) {
            this.cachedGrid = null;
            this.cachedStore = null;
            return null;
        }
        if (grid != this.cachedGrid || this.cachedStore == null) {
            this.cachedGrid = grid;
            this.cachedStore = Ae2MeStoreInvoker.sccadd$create(grid.getStorageService().getInventory(), this.level);
        }
        return this.cachedStore;
    }

    @Override
    protected List<IItemHandler> buildReaders() {
        IItemHandler handler = this.handler();
        return handler == null ? List.of() : List.of(handler);
    }

    @Nullable
    @Override
    public Object storeKey() {
        IGrid grid = this.grid();
        return grid == null ? null : grid;
    }

    @Override
    public Direction facing() {
        return Direction.NORTH;
    }

    @Override
    protected void sync() {
    }

    @Override
    public void setChanged() {
    }
}
