package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.requester.BigRow;
import com.sccadd.requester.RequesterTargetPayload;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.RequesterBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the requester aim at targets far beyond SCC's 9999 and order in bigger steps.
 *
 * <p>SCC compares an int target with a long stock. The target is kept as a long next to it, and while
 * the requester ticks, stock and target are shifted by the same amount so SCC's own comparisons and
 * its "how much is missing" stay exact for any target up to nine quadrillion.</p>
 */
@Mixin(value = RequesterBlockEntity.class, remap = false)
public abstract class RequesterBlockEntityMixin {
    @Shadow
    @Final
    private RequesterBlockEntity.Row[] rows;

    @Shadow
    protected abstract long stockOf(NetworkView view, RequesterBlockEntity.Row row);

    /** Whatever is put into a row starts switched off: you set the numbers first, then turn it on. */
    @Inject(method = "setItem", at = @At("TAIL"), require = 0)
    private void sccadd$startOff(int index, StackKey item, CallbackInfo ci) {
        if (item == null) {
            return;
        }
        RequesterBlockEntity.Row row = this.rows[Math.max(0, Math.min(5, index))];
        row.enabled = false;
        row.state = "off";
        row.nextAt = 0L;
        row.period = 0;
        ((RequesterBlockEntity) (Object) this).setChanged();
        ((NetworkBlockEntityInvoker) (Object) this).sccadd$sync();
    }

    @ModifyConstant(method = "setAmount", constant = @Constant(intValue = 9999), require = 0)
    private int sccadd$amountMax(int original) {
        return (int) RequesterTargetPayload.INT_CAP;
    }

    @ModifyConstant(method = "setBatch", constant = @Constant(intValue = 9999), require = 0)
    private int sccadd$batchMax(int original) {
        return (int) RequesterTargetPayload.INT_CAP;
    }

    @ModifyConstant(method = "loadAdditional", constant = @Constant(intValue = 9999), require = 0)
    private int sccadd$loadMax(int original) {
        return (int) RequesterTargetPayload.INT_CAP;
    }

    /** The most one plan request asks for (SCC: 9999). */
    @ModifyConstant(method = "askForPlan", constant = @Constant(longValue = 9999L), require = 0)
    private long sccadd$orderMax(long original) {
        return SccAddConfig.requesterMaxOrder();
    }

    private static long sccadd$shift(RequesterBlockEntity.Row row) {
        if (row.fluid) {
            return 0L;
        }
        return Math.max(0L, ((BigRow) (Object) row).sccadd$bigAmount() - row.amount);
    }

    @Redirect(method = "tickRequests", at = @At(value = "INVOKE",
            target = "Lde/scc/calculator/network/RequesterBlockEntity;stockOf(Lde/scc/calculator/network/NetworkView;Lde/scc/calculator/network/RequesterBlockEntity$Row;)J"),
            require = 0)
    private long sccadd$stockShifted(RequesterBlockEntity self, NetworkView view, RequesterBlockEntity.Row row) {
        return this.stockOf(view, row) - sccadd$shift(row);
    }

    @Redirect(method = "tickRequests", at = @At(value = "FIELD",
            target = "Lde/scc/calculator/network/RequesterBlockEntity$Row;have:J", opcode = Opcodes.GETFIELD), require = 0)
    private long sccadd$readHave(RequesterBlockEntity.Row row) {
        return row.have - sccadd$shift(row);
    }

    @Redirect(method = "tickRequests", at = @At(value = "FIELD",
            target = "Lde/scc/calculator/network/RequesterBlockEntity$Row;have:J", opcode = Opcodes.PUTFIELD), require = 0)
    private void sccadd$writeHave(RequesterBlockEntity.Row row, long value) {
        row.have = value + sccadd$shift(row);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 0)
    private void sccadd$saveTargets(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        ListTag list = tag.getList("rows", 10);
        for (int i = 0; i < 6 && i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            entry.putLong("sccadd_amount", ((BigRow) (Object) this.rows[i]).sccadd$bigAmount());
            entry.putLong("sccadd_interval", ((BigRow) (Object) this.rows[i]).sccadd$intervalSeconds());
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), require = 0)
    private void sccadd$loadTargets(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        ListTag list = tag.getList("rows", 10);
        for (int i = 0; i < 6 && i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.contains("sccadd_amount")) {
                ((BigRow) (Object) this.rows[i]).sccadd$setBigAmount(entry.getLong("sccadd_amount"));
            }
            if (entry.contains("sccadd_interval")) {
                ((BigRow) (Object) this.rows[i]).sccadd$setIntervalSeconds(entry.getLong("sccadd_interval"));
            }
        }
    }
}
