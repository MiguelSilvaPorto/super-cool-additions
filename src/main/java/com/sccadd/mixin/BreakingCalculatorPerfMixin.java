package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.breaking.BreakBuffer;
import com.sccadd.breaking.BreakStats;
import com.sccadd.breaking.BreakingBudget;
import com.sccadd.breaking.DropModels;
import com.sccadd.breaking.DropsFast;
import de.scc.calculator.SccProfiler;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.BreakingCalculatorBlockEntity;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.BreakingGrade;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Final;
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

    @Shadow
    @Final
    private Map<StackKey, Long> taken;

    @Shadow
    protected abstract void setStatus(String value);

    @Shadow
    protected abstract IItemHandler outputAbove(Level level, BlockPos pos);

    @Shadow
    protected abstract List<ItemStack> simulate(ServerLevel server, BlockPos pos, Block block, ItemStack effective, Entity breaker);

    @Shadow
    private static boolean fits(IItemHandler out, List<ItemStack> drops) {
        throw new AssertionError();
    }

    @Shadow
    public abstract int bonusFortune();

    @Shadow
    @Final
    private String[] filters;

    @Shadow
    private int next;

    @Shadow
    @Final
    private int[] tagTurn;

    @Shadow
    protected abstract List<List<StackKey>> cachedPlan(NetworkView view, ServerLevel server);

    @Shadow
    public abstract int overdriveStep();

    @Shadow
    public abstract int blocksPerOperation();

    @Shadow
    public abstract BreakingGrade grade();

    /** Results that did not fit above yet (batch mode). Its size depends on the tier. */
    @Unique
    private final BreakBuffer sccadd$buffer = new BreakBuffer();

    /** Blocks each key still has to receive in this work call (the dry run of SCC's round-robin). */
    @Unique
    private Map<StackKey, Integer> sccadd$need;

    @Unique
    private int sccadd$deferred;

    @Unique
    private long sccadd$deferFrom;

    /** The last work call batched something: only then it is worth letting blocks pile up. */
    @Unique
    private boolean sccadd$hot;

    @Unique
    private boolean sccadd$batchedNow;

    /** Blocks already processed in a batch that the loop has not asked for yet. */
    @Unique
    private final Map<StackKey, Integer> sccadd$credits = new HashMap<>();

    @Unique
    private net.minecraft.world.item.Item sccadd$fortuneItem;

    @Unique
    private int sccadd$fortuneHash;

    @Unique
    private int sccadd$fortuneBonus;

    @Unique
    private ItemStack sccadd$fortuneStack = ItemStack.EMPTY;

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
        this.sccadd$profileStart = 0L;
        if (this.sccadd$shouldDefer(level, blocks)) {
            // Let the blocks pile up: SCC adds them to the next call (carry), where one big batch handles them.
            this.carry = this.overdriveStep() > 0 ? blocks : blocks + this.blocksPerOperation();
            this.sccadd$workStart = 0L;
            ci.cancel();
            return;
        }
        this.sccadd$deferred = 0;
        this.sccadd$need = null;
        this.sccadd$batchedNow = false;
        this.sccadd$workStart = System.nanoTime();
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
        if (!this.sccadd$buffer.isEmpty()) {
            IItemHandler above = this.outputAbove(level, pos);
            if (above != null) {
                long before = this.sccadd$buffer.total();
                this.sccadd$buffer.flushInto(above);
                if (before != this.sccadd$buffer.total()) {
                    ((BlockEntity) (Object) this).setChanged();
                }
            }
        }
    }

    @Unique
    private boolean sccadd$shouldDefer(Level level, int blocks) {
        int target = SccAddConfig.accumulateTarget();
        if (!SccAddConfig.batchBreaking() || target <= 0 || !this.sccadd$hot || blocks >= target
                || !this.sccadd$buffer.isEmpty()) {
            this.sccadd$deferred = 0;
            return false;
        }
        long now = level.getGameTime();
        if (this.sccadd$deferred == 0) {
            this.sccadd$deferFrom = now;
        } else if (now - this.sccadd$deferFrom >= 20L) {
            return false; // at most about a second of waiting
        }
        this.sccadd$deferred++;
        BreakStats.deferred();
        return true;
    }

    /**
     * The same round-robin SCC runs, but only counting: how many blocks of each kind this call will
     * break, given the stock. Slots whose ore is running out hand their share on to the others,
     * exactly like SCC's loop does. Nothing is broken and no state is changed.
     */
    @Unique
    private Map<StackKey, Integer> sccadd$dryRun(NetworkView view, ServerLevel server, Map<StackKey, Long> stock, int blocks) {
        Map<StackKey, Integer> quota = new HashMap<>();
        Map<StackKey, Long> sim = new HashMap<>();
        List<List<StackKey>> plan = this.cachedPlan(view, server);
        int[] turns = java.util.Arrays.copyOf(this.tagTurn, this.tagTurn.length);
        int at = this.next;
        int remaining = blocks;
        while (remaining > 0) {
            boolean did = false;
            for (int step = 0; step < 27; step++) {
                int index = (at + step) % 27;
                if (this.filters[index] == null) {
                    continue;
                }
                List<StackKey> options = plan.get(index);
                if (options == null || options.isEmpty()) {
                    continue;
                }
                int pick = Math.floorMod(turns[index], options.size());
                turns[index] = pick + 1;
                StackKey want = options.get(pick);
                long have = stock.getOrDefault(want, 0L) - this.taken.getOrDefault(want, 0L) - sim.getOrDefault(want, 0L);
                if (have > 0L && Block.byItem(want.item()) != Blocks.AIR) {
                    quota.merge(want, 1, Integer::sum);
                    sim.merge(want, 1L, Long::sum);
                    at = (index + 1) % 27;
                    remaining--;
                    did = true;
                    break;
                }
            }
            if (!did) {
                break;
            }
        }
        // Blocks already paid in an earlier call are asked for first, so only the rest needs a new batch.
        for (Map.Entry<StackKey, Integer> paid : this.sccadd$credits.entrySet()) {
            Integer wanted = quota.get(paid.getKey());
            if (wanted != null && paid.getValue() > 0) {
                quota.put(paid.getKey(), Math.max(0, wanted - paid.getValue()));
            }
        }
        return quota;
    }

    @Inject(method = "work", at = @At("RETURN"), require = 0)
    private void sccadd$endWork(Level level, BlockPos pos, int blocks, CallbackInfo ci) {
        if (this.sccadd$workStart != 0L) {
            this.sccadd$hot = this.sccadd$batchedNow;
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
            return;
        }
        if (SccAddConfig.batchBreaking() && key != null
                && this.sccadd$batch(view, server, pos, key, effective, breaker, out, stock, budget)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private long sccadd$capacity() {
        return (long) SccAddConfig.bufferBase() << this.grade().ordinal();
    }

    /**
     * Batch break: takes up to {@code budget} blocks of one kind from the network at once, sums their
     * drops from the dice table and delivers the total. The loop still counts one block per call, so
     * the other blocks of the batch are paid back as credits (and wear the tool the way SCC does).
     *
     * @return true when this call is already paid (one block broken)
     */
    @Unique
    private boolean sccadd$batch(NetworkView view, ServerLevel server, BlockPos pos, StackKey key, ItemStack effective,
                                 Entity breaker, IItemHandler out, Map<StackKey, Long> stock, int budget) {
        if (this.sccadd$need == null) {
            this.sccadd$need = this.sccadd$dryRun(view, server, stock, budget);
        }
        Integer credit = this.sccadd$credits.get(key);
        if (credit != null && credit > 0) {
            this.sccadd$credits.put(key, credit - 1);
            BreakStats.credit();
            return true;
        }
        Integer wanted = this.sccadd$need.get(key);
        if (wanted == null || wanted < 2) {
            return false;
        }
        Block block = Block.byItem(key.item());
        if (block == Blocks.AIR) {
            return false;
        }
        long have = stock.getOrDefault(key, 0L) - this.taken.getOrDefault(key, 0L);
        if (have < 2L) {
            return false;
        }
        DropModels.Model model = DropModels.modelFor(block, effective);
        if (!model.ready()) {
            BreakStats.noModel();
            return false; // still learning from real breaks
        }
        int n = (int) Math.min(Math.min((long) wanted, have), 4096L);
        if (this.toolWears()) {
            ItemStack held = this.tool.getStackInSlot(0);
            if (held.isEmpty() || !held.isDamageableItem()) {
                return false;
            }
            // Keep one use in reserve: the loop wears the tool for the block of this call itself.
            n = Math.min(n, held.getMaxDamage() - held.getDamageValue() - 1);
        }
        if (n < 2) {
            return false;
        }
        n = Math.min(n, view.extractFree(key, n, true).getCount());
        if (n < 2) {
            return false;
        }
        long bufferFree = this.sccadd$buffer.free(this.sccadd$capacity());
        List<ItemStack> drops = null;
        for (int attempt = 0; attempt < 6 && n >= 2; attempt++) {
            List<ItemStack> candidate = model.sum(n);
            long items = 0L;
            for (ItemStack stack : candidate) {
                items += stack.getCount();
            }
            // Room is needed somewhere: above, or in the hidden buffer.
            if (bufferFree >= items || fits(out, candidate)) {
                drops = candidate;
                break;
            }
            n /= 2;
        }
        if (drops == null || n < 2) {
            BreakStats.noRoom();
            return false; // no room even for a small batch: SCC's own path reports "full"
        }
        ItemStack got = view.extractFree(key, n, false);
        if (got.isEmpty()) {
            return false;
        }
        if (got.getCount() != n) {
            n = got.getCount();
            drops = model.sum(n);
        }
        this.taken.merge(key, (long) n, Long::sum);
        long before = this.sccadd$buffer.total();
        for (ItemStack drop : drops) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(out, drop.copy(), false);
            if (!rest.isEmpty()) {
                if (this.sccadd$buffer.free(this.sccadd$capacity()) >= rest.getCount()) {
                    BreakStats.buffered(rest.getCount());
                    this.sccadd$buffer.add(rest);
                } else {
                    ItemStack leftover = view.insert(rest, false);
                    if (!leftover.isEmpty()) {
                        Block.popResource(server, pos, leftover); // never lose material
                    }
                }
            }
        }
        if (before != this.sccadd$buffer.total()) {
            ((BlockEntity) (Object) this).setChanged();
        }
        // One real result per batch keeps the dice table following the real loot.
        this.simulate(server, pos, block, effective, breaker);
        this.sccadd$credits.put(key, n - 1);
        this.sccadd$need.put(key, Math.max(0, wanted - n));
        this.sccadd$batchedNow = true;
        BreakStats.batch(n);
        return true;
    }

    /** Every real result of the break pipeline teaches the dice table of that block and tool. */
    @Inject(method = "simulate", at = @At("RETURN"), require = 0)
    private void sccadd$learn(ServerLevel server, BlockPos pos, Block block, ItemStack effective, Entity breaker,
                              CallbackInfoReturnable<List<ItemStack>> cir) {
        if (SccAddConfig.batchBreaking()) {
            DropModels.feed(block, effective, cir.getReturnValue());
            if (SccAddConfig.profile()) {
                BreakStats.learn();
                BreakStats.maybeLog();
            }
        }
    }

    // ------------------------------------------------------------------
    // the pickaxe with the bonus fortune is rebuilt only when it changes
    // ------------------------------------------------------------------

    @Unique
    private int sccadd$enchantHash(ItemStack pick) {
        return pick.getOrDefault(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).hashCode();
    }

    @Inject(method = "withBonusFortune", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$fortuneCached(ServerLevel server, ItemStack pick, CallbackInfoReturnable<ItemStack> cir) {
        int bonus = this.bonusFortune();
        if (bonus > 0 && !this.sccadd$fortuneStack.isEmpty() && this.sccadd$fortuneItem == pick.getItem()
                && this.sccadd$fortuneBonus == bonus && this.sccadd$fortuneHash == this.sccadd$enchantHash(pick)) {
            cir.setReturnValue(this.sccadd$fortuneStack);
        }
    }

    @Inject(method = "withBonusFortune", at = @At("RETURN"), require = 0)
    private void sccadd$fortuneRemember(ServerLevel server, ItemStack pick, CallbackInfoReturnable<ItemStack> cir) {
        int bonus = this.bonusFortune();
        ItemStack result = cir.getReturnValue();
        if (bonus > 0 && result != null && result != pick) {
            this.sccadd$fortuneItem = pick.getItem();
            this.sccadd$fortuneBonus = bonus;
            this.sccadd$fortuneHash = this.sccadd$enchantHash(pick);
            this.sccadd$fortuneStack = result;
        }
    }

    // ------------------------------------------------------------------
    // the hidden buffer: saved with the block, dropped with it
    // ------------------------------------------------------------------

    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 0)
    private void sccadd$saveBuffer(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.sccadd$buffer.save(tag, registries);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), require = 0)
    private void sccadd$loadBuffer(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.sccadd$buffer.load(tag, registries);
    }

    @Inject(method = "dropTool", at = @At("HEAD"), require = 0)
    private void sccadd$dropBuffer(Level level, BlockPos pos, CallbackInfo ci) {
        this.sccadd$buffer.dropAll(level, pos);
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
