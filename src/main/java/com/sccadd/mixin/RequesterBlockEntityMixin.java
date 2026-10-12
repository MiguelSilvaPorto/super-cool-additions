package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.chain.ChainData;
import com.sccadd.chain.ChainHolder;
import com.sccadd.chain.ChainRunner;
import com.sccadd.requester.BigRow;
import com.sccadd.requester.RequesterTargetPayload;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.RequesterBlockEntity;
import de.scc.calculator.network.TerminalBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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
public abstract class RequesterBlockEntityMixin implements ChainHolder {
    @Shadow
    @Final
    private RequesterBlockEntity.Row[] rows;

    @Unique
    private final ChainData[] sccadd$chains = new ChainData[6];
    @Unique
    private final ChainRunner[] sccadd$runners = {new ChainRunner(), new ChainRunner(), new ChainRunner(),
            new ChainRunner(), new ChainRunner(), new ChainRunner()};

    @Shadow
    protected abstract TerminalBlockEntity firstTerminal(NetworkView view);

    @Shadow
    protected abstract long stockOf(NetworkView view, RequesterBlockEntity.Row row);

    /** Whatever is put into a row starts switched off: you set the numbers first, then turn it on. */
    @Inject(method = "setItem", at = @At("TAIL"), require = 0)
    private void sccadd$startOff(int index, StackKey item, CallbackInfo ci) {
        if (item == null) {
            return;
        }
        RequesterBlockEntity.Row row = this.rows[Math.max(0, Math.min(5, index))];
        this.sccadd$chains[Math.max(0, Math.min(5, index))] = null;
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

    @Inject(method = "clearRow", at = @At("TAIL"), require = 0)
    private void sccadd$clearChain(int index, CallbackInfo ci) {
        if (index >= 0 && index < 6) {
            this.sccadd$chains[index] = null;
        }
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

    @Override
    public ChainData sccadd$chain(int row) {
        return row >= 0 && row < 6 ? this.sccadd$chains[row] : null;
    }

    @Override
    public void sccadd$setChain(int row, ChainData chain) {
        if (row < 0 || row >= 6) {
            return;
        }
        this.sccadd$chains[row] = chain == null || chain.isEmpty() ? null : chain;
        this.sccadd$runners[row].reset();
        if (this.sccadd$chains[row] != null) {
            // the start box is the beginning and the goal of the chain: the row shows its item
            ChainData.Node start = this.sccadd$chains[row].start();
            StackKey key = start == null || start.item.isEmpty() ? null : StackKey.decode(start.item);
            if (key != null) {
                this.rows[row].item = key;
            }
            NetworkView now = ((RequesterBlockEntity) (Object) this).network();
            if (now != null) {
                ChainRunner.countStock(this.sccadd$chains[row], now);
            }
        }
        ((RequesterBlockEntity) (Object) this).setChanged();
        ((NetworkBlockEntityInvoker) (Object) this).sccadd$sync();
    }

    @Unique
    private boolean sccadd$chained(RequesterBlockEntity.Row row) {
        for (int i = 0; i < 6; i++) {
            if (this.rows[i] == row && this.sccadd$chains[i] != null) {
                return true;
            }
        }
        return false;
    }

    /** A row that has a chain is driven by the chain, not by SCC's own stock/order logic. */
    @Redirect(method = "tickRequests", at = @At(value = "FIELD",
            target = "Lde/scc/calculator/network/RequesterBlockEntity$Row;enabled:Z", opcode = Opcodes.GETFIELD), require = 0)
    private boolean sccadd$chainOwnsRow(RequesterBlockEntity.Row row) {
        return row.enabled && !this.sccadd$chained(row);
    }

    /** ...and SCC must not write its own state over the chain's. */
    @Redirect(method = "tickRequests", at = @At(value = "FIELD",
            target = "Lde/scc/calculator/network/RequesterBlockEntity$Row;state:Ljava/lang/String;", opcode = Opcodes.PUTFIELD), require = 0)
    private void sccadd$keepChainState(RequesterBlockEntity.Row row, String value) {
        if (!this.sccadd$chained(row)) {
            row.state = value;
        }
    }

    @Inject(method = "tickRequests", at = @At("RETURN"), require = 0)
    private void sccadd$runChains(CallbackInfo ci) {
        RequesterBlockEntity self = (RequesterBlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide || self.getLevel().getGameTime() % 20 != 7) {
            return;
        }
        NetworkView view = self.network();
        if (view == null || !view.online()) {
            return;
        }
        TerminalBlockEntity terminal = null;
        boolean changed = false;
        for (int i = 0; i < 6; i++) {
            ChainData chain = this.sccadd$chains[i];
            RequesterBlockEntity.Row row = this.rows[i];
            if (chain == null) {
                continue;
            }
            changed |= ChainRunner.countStock(chain, view);
            if (!row.enabled) {
                continue;
            }
            if (terminal == null) {
                terminal = this.firstTerminal(view);
            }
            String before = row.state;
            this.sccadd$runners[i].tick(row, chain, view, terminal, self.getLevel().getGameTime(), self.getBlockPos().toShortString() + "#" + i);
            changed |= !java.util.Objects.equals(before, row.state);
        }
        if (changed) {
            ((NetworkBlockEntityInvoker) (Object) this).sccadd$sync();
        }
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 0)
    private void sccadd$saveChains(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        ListTag list = new ListTag();
        for (int i = 0; i < 6; i++) {
            list.add(this.sccadd$chains[i] == null ? new CompoundTag() : this.sccadd$chains[i].save());
        }
        tag.put("sccadd_chains", list);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), require = 0)
    private void sccadd$loadChains(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        ListTag list = tag.getList("sccadd_chains", 10);
        for (int i = 0; i < 6; i++) {
            ChainData chain = i < list.size() ? ChainData.load(list.getCompound(i)) : null;
            this.sccadd$chains[i] = chain == null || chain.isEmpty() ? null : chain;
        }
    }
}
