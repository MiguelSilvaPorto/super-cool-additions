package com.sccadd.mixin;

import com.sccadd.iface.InterfaceTanks;
import com.sccadd.iface.TankHolder;
import de.scc.calculator.network.InterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives the Interface nine fluid tanks beside its nine item slots (see {@link InterfaceTanks}). */
@Mixin(value = InterfaceBlockEntity.class, remap = false)
public abstract class InterfaceTanksMixin implements TankHolder {
    @Unique
    private InterfaceTanks sccadd$tanks;

    @Override
    public InterfaceTanks sccadd$tanks() {
        if (this.sccadd$tanks == null) {
            this.sccadd$tanks = new InterfaceTanks((InterfaceBlockEntity) (Object) this);
        }
        return this.sccadd$tanks;
    }

    @Inject(method = "serverTick", at = @At("RETURN"), require = 0)
    private static void sccadd$tickTanks(Level level, BlockPos pos, BlockState state, InterfaceBlockEntity be, CallbackInfo ci) {
        InterfaceTanks tanks = ((TankHolder) be).sccadd$tanks();
        tanks.tick();
        if (be.getLevel() != null && be.getLevel().getGameTime() % 10 == 4 && tanks.takeDirty()) {
            ((NetworkBlockEntityInvoker) (Object) be).sccadd$sync();
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 0)
    private void sccadd$saveTanks(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        this.sccadd$tanks().save(tag, provider);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), require = 0)
    private void sccadd$loadTanks(CompoundTag tag, HolderLookup.Provider provider, CallbackInfo ci) {
        this.sccadd$tanks().load(tag, provider);
    }
}
