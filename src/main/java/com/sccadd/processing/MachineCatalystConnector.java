package com.sccadd.processing;

import de.scc.calculator.network.ProcessingCalculatorBlockEntity;
import de.scc.calculator.network.SccRegistry;
import de.scc.calculator.network.StorageConnectorBlockEntity;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * A read-only storage that is not a block: the reusable catalysts sitting inside the machine a
 * Processing Calculator feeds, shown to the network as stock. SCC then knows the press is already
 * there and neither plans nor orders another one; the job itself already skips a catalyst the
 * machine holds. Nothing can be taken from or put into this storage.
 */
public final class MachineCatalystConnector extends StorageConnectorBlockEntity {
    private final ProcessingCalculatorBlockEntity calculator;
    private final IItemHandler view = new View();

    MachineCatalystConnector(ProcessingCalculatorBlockEntity calculator) {
        super(calculator.getBlockPos(), SccRegistry.STORAGE_CONNECTOR.get().defaultBlockState());
        this.calculator = calculator;
        this.setLevel(calculator.getLevel());
    }

    @Nullable
    @Override
    public IItemHandler handler() {
        return this.calculator.hasInventory() ? this.view : null;
    }

    @Override
    protected List<IItemHandler> buildReaders() {
        IItemHandler handler = this.handler();
        return handler == null ? List.of() : List.of(handler);
    }

    /** One entry per machine, even when two calculators face the same one. */
    @Nullable
    @Override
    public Object storeKey() {
        return this.calculator.hasInventory() ? "sccadd-catalyst@" + this.calculator.machinePos() : null;
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

    private final class View implements IItemHandler {
        @Nullable
        private IItemHandler inner() {
            return MachineCatalystConnector.this.calculator.handler();
        }

        @Override
        public int getSlots() {
            IItemHandler inner = this.inner();
            return inner == null ? 0 : inner.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            IItemHandler inner = this.inner();
            if (inner == null || slot < 0 || slot >= inner.getSlots()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = inner.getStackInSlot(slot);
            return Catalysts.is(stack) ? stack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
