package com.sccadd.mixin;

import de.scc.calculator.network.InterfaceBlockEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A slot with a template is kept stocked from the network, but SCC still lets pipes and machines
 * push other items into it whenever it is empty. With a constant input the slot refills with the
 * foreign item between two balancing cycles, so the template item is never delivered. Outside
 * inserts into a templated slot are refused; only the network fills it.
 */
@Mixin(value = InterfaceBlockEntity.class, remap = false)
public abstract class InterfaceTemplateGuardMixin {
    @Shadow
    private IItemHandler outside;

    @Unique
    private IItemHandler sccadd$raw;
    @Unique
    private IItemHandler sccadd$guarded;

    @Inject(method = "handler", at = @At("RETURN"), cancellable = true, require = 0)
    private void sccadd$guardHandler(CallbackInfoReturnable<IItemHandler> cir) {
        cir.setReturnValue(this.sccadd$wrap(cir.getReturnValue()));
    }

    @Inject(method = "handlerFor", at = @At("RETURN"), cancellable = true, require = 0)
    private void sccadd$guardHandlerFor(net.minecraft.core.Direction side, CallbackInfoReturnable<IItemHandler> cir) {
        cir.setReturnValue(this.sccadd$wrap(cir.getReturnValue()));
    }

    @Unique
    private IItemHandler sccadd$wrap(IItemHandler handler) {
        if (handler == null || handler instanceof Guard || handler != this.outside) {
            return handler;
        }
        if (handler != this.sccadd$raw) {
            this.sccadd$raw = handler;
            this.sccadd$guarded = new Guard(handler, (InterfaceBlockEntity) (Object) this);
        }
        return this.sccadd$guarded;
    }

    @Unique
    private static final class Guard implements IItemHandler {
        private final IItemHandler inner;
        private final InterfaceBlockEntity owner;

        Guard(IItemHandler inner, InterfaceBlockEntity owner) {
            this.inner = inner;
            this.owner = owner;
        }

        private boolean templated(int slot) {
            String entry = this.owner.filter().entry(slot);
            return entry != null && !entry.isEmpty() && !entry.startsWith(com.sccadd.iface.InterfaceTanks.PREFIX);
        }

        @Override
        public int getSlots() {
            return this.inner.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.inner.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return this.templated(slot) ? stack : this.inner.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return this.inner.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.inner.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !this.templated(slot) && this.inner.isItemValid(slot, stack);
        }
    }
}
