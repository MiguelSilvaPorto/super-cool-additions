package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import de.scc.calculator.network.DistributorScan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the Stellar Distributor's FE module away from AE2 blocks.
 *
 * <p>An AE2 cable bus exposes FE when it carries an Energy Acceptor or an FE P2P
 * tunnel (1 FE = 0.5 AE). The distributor then pushes into the ME network at its
 * own rate - and on the Singularity tier that rate is unlimited. Feeding power
 * back from the ME into the SCC network (the Quartz Fiber bridge) would then close
 * an endless loop: SCC -> distributor -> ME -> fiber -> SCC. The antenna stays as
 * the normal SCC to ME path, it is pull based and pays FE per AE.</p>
 *
 * <p>SCC has the same guard for other energy teleporters already (the
 * {@code scc:distributor_ignored} tag with Flux Networks and Mekanism). This does
 * it for AE2 without touching the tag, so it can be switched off again.</p>
 */
@Mixin(value = DistributorScan.class, remap = false)
public abstract class Ae2DistributorScanMixin {
    @Unique
    private static final String sccadd$AE2 = "ae2";

    @Inject(method = "examine", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sccadd$skipAe2PowerSink(ServerLevel level, BlockEntity be,
                                                CallbackInfoReturnable<DistributorScan.Hit> cir) {
        if (be == null || !SccAddConfig.distributorIgnoresAe2()) {
            return;
        }
        if (sccadd$isAe2(be) && sccadd$acceptsFe(level, be)) {
            cir.setReturnValue(null);
        }
    }

    @Unique
    private static boolean sccadd$isAe2(BlockEntity be) {
        ResourceLocation type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
        if (type != null && sccadd$AE2.equals(type.getNamespace())) {
            return true;
        }
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock());
        return block != null && sccadd$AE2.equals(block.getNamespace());
    }

    /** True when the block offers FE that something could push into (the distributor's targets). */
    @Unique
    private static boolean sccadd$acceptsFe(ServerLevel level, BlockEntity be) {
        BlockPos pos = be.getBlockPos();
        for (Direction side : Direction.values()) {
            IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
            if (storage != null && storage.canReceive()) {
                return true;
            }
        }
        return false;
    }
}
