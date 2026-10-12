package com.sccadd.mixin;

import com.sccadd.requester.BigRow;
import com.sccadd.requester.RequesterTargetPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A requester row with a long target next to SCC's int one. */
@Mixin(targets = "de.scc.calculator.network.RequesterBlockEntity$Row", remap = false)
public abstract class RequesterRowMixin implements BigRow {
    @Shadow
    public int amount;

    @Shadow
    public int interval;

    @Shadow
    de.scc.calculator.network.CraftJob job;

    @Override
    public boolean sccadd$jobRunning() {
        return this.job != null && !this.job.isFinished();
    }

    @Unique
    private long sccadd$seconds;

    @Unique
    private int sccadd$secondsIndex = -1;

    @Unique
    private long sccadd$big;

    @Override
    public long sccadd$bigAmount() {
        // Valid only while SCC's int still matches it (SCC resets the int on its own paths).
        if (this.sccadd$big > 0L && (int) Math.min(RequesterTargetPayload.INT_CAP, this.sccadd$big) == this.amount) {
            return this.sccadd$big;
        }
        return this.amount;
    }

    @Override
    public void sccadd$setBigAmount(long value) {
        this.sccadd$big = value;
    }

    @Override
    public long sccadd$intervalSeconds() {
        // Free seconds hold only while SCC's preset index is still the one they were set with.
        return this.sccadd$seconds > 0L && this.sccadd$secondsIndex == this.interval ? this.sccadd$seconds : 0L;
    }

    @Override
    public void sccadd$setIntervalSeconds(long seconds) {
        this.sccadd$seconds = seconds;
        this.sccadd$secondsIndex = this.interval;
    }

    @Inject(method = "intervalSeconds", at = @At("RETURN"), cancellable = true, require = 0)
    private void sccadd$freeInterval(CallbackInfoReturnable<Integer> cir) {
        long free = this.sccadd$intervalSeconds();
        if (free > 0L) {
            cir.setReturnValue((int) Math.min(Integer.MAX_VALUE, free));
        }
    }

    @ModifyConstant(method = "batchSize", constant = @Constant(intValue = 9999), require = 0)
    private int sccadd$batchMax(int original) {
        return (int) RequesterTargetPayload.INT_CAP;
    }
}
