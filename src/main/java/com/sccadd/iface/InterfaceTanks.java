package com.sccadd.iface;

import de.scc.calculator.network.InterfaceBlockEntity;
import de.scc.calculator.network.NetworkView;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Nine fluid tanks for the SCC Interface, one next to each item slot - like an ME Interface that
 * does items and fluids at the same time.
 *
 * <p>A cell whose template is {@code fluid:<id>} keeps its tank stocked from the network and only
 * hands the fluid out. Any other cell is an input tank: whatever a pipe or machine pours into it is
 * moved on into the network. The template is stored in the Interface's ordinary template string, so
 * SCC's own sync, save and copy/paste carry it.</p>
 */
public final class InterfaceTanks implements IFluidHandler {
    public static final String PREFIX = "fluid:";
    public static final int CAPACITY = 16000;
    private static final int SLOTS = InterfaceBlockEntity.SLOTS;

    private final InterfaceBlockEntity be;
    private final FluidStack[] tanks = new FluidStack[SLOTS];

    public InterfaceTanks(InterfaceBlockEntity be) {
        this.be = be;
        java.util.Arrays.fill(this.tanks, FluidStack.EMPTY);
    }

    @Nullable
    public static Fluid templateOf(@Nullable String entry) {
        if (entry == null || !entry.startsWith(PREFIX)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(entry.substring(PREFIX.length()));
        if (id == null) {
            return null;
        }
        Fluid fluid = BuiltInRegistries.FLUID.getOptional(id).orElse(null);
        if (fluid == null || fluid == Fluids.EMPTY) {
            return null;
        }
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    public static String entryOf(Fluid fluid) {
        Fluid source = fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
        return PREFIX + BuiltInRegistries.FLUID.getKey(source);
    }

    @Nullable
    private Fluid template(int slot) {
        return templateOf(this.be.filter().entry(slot));
    }

    /** Called every game tick by the Interface; does its work every tenth. */
    public void tick() {
        if (this.be.getLevel() == null || this.be.getLevel().isClientSide
                || (this.be.getLevel().getGameTime() + 3) % 10 != 0) {
            return;
        }
        NetworkView view = this.be.network();
        if (view == null || !view.online()) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            Fluid wanted = this.template(i);
            FluidStack held = this.tanks[i];
            if (!held.isEmpty() && (wanted == null || held.getFluid() != wanted)) {
                FluidStack left = view.insertFluid(held.copy(), false);
                if (left == null) {
                    left = FluidStack.EMPTY;
                }
                if (left.getAmount() != held.getAmount()) {
                    this.tanks[i] = left.isEmpty() ? FluidStack.EMPTY : left;
                    changed = true;
                }
            }
            if (wanted != null && this.refill(i, wanted, view)) {
                changed = true;
            }
        }
        if (changed) {
            this.be.setChanged();
        }
    }

    private boolean refill(int slot, Fluid wanted, NetworkView view) {
        FluidStack held = this.tanks[slot];
        if (!held.isEmpty() && held.getFluid() != wanted) {
            return false;
        }
        int want = CAPACITY - held.getAmount();
        if (want <= 0) {
            return false;
        }
        int got = view.drainFluid(wanted, want, false);
        if (got <= 0) {
            return false;
        }
        this.tanks[slot] = new FluidStack(wanted, held.getAmount() + got);
        return true;
    }

    private void refillFor(int slot, int need) {
        Fluid wanted = this.template(slot);
        if (wanted == null || this.tanks[slot].getAmount() >= need || this.be.getLevel() == null || this.be.getLevel().isClientSide) {
            return;
        }
        NetworkView view = this.be.network();
        if (view != null && view.online() && this.refill(slot, wanted, view)) {
            this.be.setChanged();
        }
    }

    @Override
    public int getTanks() {
        return SLOTS;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return tank >= 0 && tank < SLOTS ? this.tanks[tank] : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return CAPACITY;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return tank >= 0 && tank < SLOTS && this.template(tank) == null
                && (this.tanks[tank].isEmpty() || FluidStack.isSameFluidSameComponents(this.tanks[tank], stack));
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        int best = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (this.template(i) != null) {
                continue;
            }
            FluidStack held = this.tanks[i];
            if (!held.isEmpty() && FluidStack.isSameFluidSameComponents(held, resource)) {
                best = i;
                break;
            }
            if (held.isEmpty() && best < 0) {
                best = i;
            }
        }
        if (best < 0) {
            return 0;
        }
        FluidStack held = this.tanks[best];
        int accepted = Math.min(resource.getAmount(), CAPACITY - held.getAmount());
        if (accepted > 0 && action.execute()) {
            this.tanks[best] = resource.copyWithAmount(held.getAmount() + accepted);
            this.be.setChanged();
        }
        return Math.max(accepted, 0);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }
        for (int i = 0; i < SLOTS; i++) {
            Fluid wanted = this.template(i);
            if (wanted == null || wanted != resource.getFluid()) {
                continue;
            }
            this.refillFor(i, resource.getAmount());
            FluidStack out = this.take(i, resource.getAmount(), action);
            if (!out.isEmpty()) {
                return out;
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        for (int i = 0; i < SLOTS; i++) {
            if (this.template(i) == null) {
                continue;
            }
            this.refillFor(i, maxDrain);
            FluidStack out = this.take(i, maxDrain, action);
            if (!out.isEmpty()) {
                return out;
            }
        }
        return FluidStack.EMPTY;
    }

    private FluidStack take(int slot, int max, FluidAction action) {
        FluidStack held = this.tanks[slot];
        if (held.isEmpty() || held.getFluid() != this.template(slot)) {
            return FluidStack.EMPTY;
        }
        int moved = Math.min(max, held.getAmount());
        FluidStack out = held.copyWithAmount(moved);
        if (action.execute()) {
            this.tanks[slot] = moved >= held.getAmount() ? FluidStack.EMPTY : held.copyWithAmount(held.getAmount() - moved);
            this.be.setChanged();
        }
        return out;
    }

    public void save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        boolean any = false;
        for (FluidStack stack : this.tanks) {
            any |= !stack.isEmpty();
            list.add(stack.isEmpty() ? new CompoundTag() : stack.save(provider));
        }
        if (any) {
            tag.put("sccadd_tanks", list);
        }
    }

    public void load(CompoundTag tag, HolderLookup.Provider provider) {
        java.util.Arrays.fill(this.tanks, FluidStack.EMPTY);
        if (!tag.contains("sccadd_tanks", Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList("sccadd_tanks", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(SLOTS, list.size()); i++) {
            this.tanks[i] = FluidStack.parseOptional(provider, list.getCompound(i));
        }
    }
}
