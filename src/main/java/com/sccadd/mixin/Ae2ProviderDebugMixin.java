package com.sccadd.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import com.sccadd.SccAddConfig;
import de.scc.calculator.SccLog;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.NetworkAntennaBlockEntity;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.TerminalBlockEntity;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Temporary diagnosis aid: every few seconds it writes the state of the
 * "what can SCC build" offer to logs/scc.log, so a silent offer can be traced
 * to the exact missing piece (no CPU, no terminal, no node...).
 */
@Mixin(targets = "de.scc.calculator.compat.ae2.Ae2Provider", remap = false)
public abstract class Ae2ProviderDebugMixin {
    @Shadow
    @Final
    private IManagedGridNode node;

    @Shadow
    @Final
    private NetworkAntennaBlockEntity be;

    @Shadow
    private Map<AEKey, StackKey> offer;

    @Shadow
    private Set<AEKey> emitted;

    @Unique
    private static long sccadd$nextLog;

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void sccadd$debugOffer(CallbackInfo ci) {
        if (!SccAddConfig.ae2DebugOffer()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < sccadd$nextLog) {
            return;
        }
        sccadd$nextLog = now + 5_000L;
        try {
            Level level = this.be.getLevel();
            if (!(level instanceof ServerLevel server)) {
                return;
            }
            IGridNode gridNode = this.node.getNode();
            if (gridNode == null) {
                SccLog.info("sccadd", "offer debug at " + this.be.getBlockPos().toShortString() + ": node=null");
                return;
            }
            boolean active = gridNode.isActive();
            ICraftingService crafting = active ? gridNode.getGrid().getCraftingService() : null;
            int cpus = crafting == null ? -1 : crafting.getCpus().size();
            NetworkView view = this.be.network();
            TerminalBlockEntity terminal = view == null ? null : view.terminalNear(server);
            SccLog.info("sccadd", "offer debug at " + this.be.getBlockPos().toShortString()
                    + ": active=" + active
                    + " cpus=" + cpus
                    + " terminal=" + (terminal != null)
                    + " offer=" + this.offer.size()
                    + " emitted=" + this.emitted.size());
        } catch (Throwable failed) {
            // never let a diagnosis break the tick
        }
    }
}
