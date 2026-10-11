package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.breaking.BreakingBudget;
import com.sccadd.breaking.DropsFast;
import de.scc.calculator.SccProfiler;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.BreakingCalculatorBlockEntity;
import de.scc.calculator.network.NetworkView;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Performance patches for the Breaking Calculator.
 *
 * <ul>
 *   <li>hard per-tick budget for all calculators together (see {@link BreakingBudget}),</li>
 *   <li>loot parameters and the drop event are built much more cheaply (see {@link DropsFast}),</li>
 *   <li>tool durability is synced once per tick instead of once per broken block,</li>
 *   <li>optional fine-grained marks for {@code /scc profile}.</li>
 * </ul>
 */
@Mixin(value = BreakingCalculatorBlockEntity.class, remap = false)
public abstract class BreakingCalculatorPerfMixin {
    @Shadow
    private transient int carry;

    @Shadow
    private ItemStackHandler tool;

    @Shadow
    public abstract boolean toolWears();

    @Unique
    private long sccadd$deadline;

    @Unique
    private long sccadd$workStart;

    @Unique
    private long sccadd$profileStart;

    @Unique
    private boolean sccadd$toolDirty;

    // ------------------------------------------------------------------
    // budget + profiling around work()
    // ------------------------------------------------------------------

    @Inject(method = "work", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$beginWork(Level level, BlockPos pos, int blocks, CallbackInfo ci) {
        this.sccadd$workStart = System.nanoTime();
        this.sccadd$profileStart = 0L;
        if (level instanceof ServerLevel server) {
            long budget = BreakingBudget.begin(server);
            if (budget <= 0L) {
                // The whole tick's budget is gone: keep the blocks for the next tick.
                this.carry = blocks;
                this.sccadd$workStart = 0L;
                ci.cancel();
                return;
            }
            this.sccadd$deadline = this.sccadd$workStart + budget;
        } else {
            this.sccadd$deadline = Long.MAX_VALUE;
        }
        if (SccAddConfig.profile()) {
            this.sccadd$profileStart = SccProfiler.begin();
        }
    }

    @Inject(method = "work", at = @At("RETURN"), require = 0)
    private void sccadd$endWork(Level level, BlockPos pos, int blocks, CallbackInfo ci) {
        if (this.sccadd$workStart != 0L) {
            BreakingBudget.record(System.nanoTime() - this.sccadd$workStart);
            this.sccadd$workStart = 0L;
        }
        if (this.sccadd$profileStart != 0L) {
            SccProfiler.end("sccadd breaking work", this.sccadd$profileStart);
            this.sccadd$profileStart = 0L;
        }
    }

    /**
     * Checked before every single block. SCC's own 2 ms check only starts after the
     * first successful break; this one also guards the very first block of a call
     * and honours the configurable budget.
     */
    @Inject(method = "breakOne", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$budgetGate(NetworkView view, ServerLevel server, BlockPos pos, StackKey key, ItemStack effective,
                                   Entity breaker, IItemHandler out, Map<StackKey, Long> stock, int budget,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (this.sccadd$deadline != 0L && System.nanoTime() > this.sccadd$deadline) {
            this.carry = budget;
            cir.setReturnValue(false);
        }
    }

    // ------------------------------------------------------------------
    // cheap loot roll and drop event inside simulate()
    // ------------------------------------------------------------------

    @Redirect(
            method = "simulate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;"
            ),
            require = 0
    )
    private List<ItemStack> sccadd$fastDrops(BlockState state, ServerLevel level, BlockPos pos, BlockEntity be,
                                             Entity entity, ItemStack tool) {
        return DropsFast.getDrops(this, state, level, pos, be, entity, tool);
    }

    @Redirect(
            method = "simulate",
            at = @At(
                    value = "INVOKE",
                    target = "Lde/scc/calculator/compat/DropEvents;apply(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/List;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;)Ljava/util/List;",
                    remap = false
            ),
            require = 0
    )
    private List<ItemStack> sccadd$fastDropEvent(ServerLevel server, BlockPos pos, BlockState state,
                                                 List<ItemStack> drops, ItemStack tool, Entity breaker) {
        return DropsFast.applyDropsEvent(server, pos, state, drops, tool, breaker);
    }

    // ------------------------------------------------------------------
    // tool durability: one sync per tick instead of one per block
    // ------------------------------------------------------------------

    /**
     * Same as SCC's {@code wearTool}, minus the per-block {@code setChanged()}
     * and {@code sync()}: those are collected in a flag and flushed once per tick
     * from {@code serverTick}.
     */
    @Inject(method = "wearTool", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$wearTool(ServerLevel server, CallbackInfoReturnable<Boolean> cir) {
        if (!SccAddConfig.deferToolSync()) {
            return;
        }
        if (!this.toolWears()) {
            cir.setReturnValue(true);
            return;
        }
        ItemStack stack = this.tool.getStackInSlot(0);
        if (!stack.isEmpty() && stack.isDamageableItem()) {
            stack.hurtAndBreak(1, server, null, item -> {
            });
            this.sccadd$toolDirty = true;
            cir.setReturnValue(!stack.isEmpty());
        } else {
            cir.setReturnValue(!stack.isEmpty());
        }
    }

    @Inject(method = "serverTick", at = @At("TAIL"), require = 0)
    private static void sccadd$flushToolSync(Level level, BlockPos pos, BlockState state,
                                             BreakingCalculatorBlockEntity be, CallbackInfo ci) {
        BreakingCalculatorPerfMixin mixin = (BreakingCalculatorPerfMixin) (Object) be;
        if (mixin.sccadd$toolDirty) {
            mixin.sccadd$toolDirty = false;
            be.setChanged();
            ((NetworkBlockEntityInvoker) be).sccadd$sync();
        }
    }
}
