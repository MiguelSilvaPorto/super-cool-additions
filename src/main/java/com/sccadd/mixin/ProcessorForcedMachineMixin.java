package com.sccadd.mixin;

import com.sccadd.machine.MachineForce;
import de.scc.calculator.network.ProcessingCalculatorBlockEntity;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a Processing Calculator be told which machine it is. SCC reads the block in front and
 * takes its id as the machine; with a chest in front that is "chest", so no recipe ever matches.
 * Forced to "furnace", it accepts furnace jobs and hands the items to whatever stands in front
 * (the chest), where pipes, hoppers or any outside automation take over.
 */
@Mixin(value = ProcessingCalculatorBlockEntity.class, remap = false)
public abstract class ProcessorForcedMachineMixin implements MachineForce {
    @Shadow
    protected ResourceLocation machineId;

    @Shadow
    protected String category;

    @Shadow
    protected boolean hasInventory;

    @Shadow
    protected abstract boolean detectInventory();

    @Unique
    private ResourceLocation sccadd$forced;

    @Override
    public ResourceLocation sccadd$forced() {
        return this.sccadd$forced;
    }

    @Override
    public void sccadd$setForced(ResourceLocation machine) {
        this.sccadd$forced = machine;
        ProcessingCalculatorBlockEntity self = (ProcessingCalculatorBlockEntity) (Object) this;
        if (machine == null) {
            this.category = null;
            this.machineId = null;
        }
        self.detect();
        self.setChanged();
        ((NetworkBlockEntityInvoker) (Object) this).sccadd$sync();
    }

    @Inject(method = "detect", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$detectForced(CallbackInfo ci) {
        ProcessingCalculatorBlockEntity self = (ProcessingCalculatorBlockEntity) (Object) this;
        if (this.sccadd$forced == null || self.getLevel() == null || self.getLevel().isClientSide) {
            return;
        }
        boolean inventory = this.detectInventory();
        boolean changed = !Objects.equals(this.machineId, this.sccadd$forced) || this.hasInventory != inventory;
        this.machineId = this.sccadd$forced;
        this.category = this.sccadd$forced.toString();
        this.hasInventory = inventory;
        if (changed) {
            ((NetworkBlockEntityInvoker) (Object) this).sccadd$sync();
        }
        ci.cancel();
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 0)
    private void sccadd$saveForced(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (this.sccadd$forced != null) {
            tag.putString("sccadd_machine", this.sccadd$forced.toString());
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), require = 0)
    private void sccadd$loadForced(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.sccadd$forced = tag.contains("sccadd_machine") ? ResourceLocation.tryParse(tag.getString("sccadd_machine")) : null;
    }
}
