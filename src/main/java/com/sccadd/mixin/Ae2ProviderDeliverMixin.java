package com.sccadd.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.sccadd.SccAddConfig;
import com.sccadd.ae2.SccAe2Delivery;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.NetworkAntennaBlockEntity;
import de.scc.calculator.network.NetworkView;
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
 * The antenna's own delivery loop only covers the emitable items. For items that
 * have a real pattern (exposed by the addon), AE2 pushed the ingredients but still
 * waits for the output - this closes that gap.
 */
@Mixin(targets = "de.scc.calculator.compat.ae2.Ae2Provider", remap = false)
public abstract class Ae2ProviderDeliverMixin {
    @Shadow
    @Final
    private NetworkAntennaBlockEntity be;

    @Shadow
    @Final
    private IManagedGridNode node;

    @Shadow
    private Map<AEKey, StackKey> offer;

    @Shadow
    private Set<AEKey> emitted;

    @Unique
    private int sccadd$deliverTimer;

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void sccadd$deliverPatternOutputs(CallbackInfo ci) {
        if (!SccAddConfig.sccPatternsInAe2()) {
            return;
        }
        if (++this.sccadd$deliverTimer % 5 != 0) {
            return;
        }
        Level level = this.be.getLevel();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        NetworkView view = this.be.network();
        if (view == null || !view.online() || this.offer.isEmpty()) {
            return;
        }
        IGridNode gridNode = this.node.getNode();
        if (gridNode == null || !gridNode.isActive()) {
            return;
        }
        ICraftingService crafting = gridNode.getGrid().getCraftingService();
        MEStorage inventory = gridNode.getGrid().getStorageService().getInventory();
        for (AEKey key : this.offer.keySet()) {
            if (this.emitted.contains(key)) {
                continue; // SCC's own service loop handles the emitable ones
            }
            if (!(key instanceof AEItemKey item) || !crafting.isRequesting(key)) {
                continue;
            }
            StackKey wanted = this.offer.get(key);
            if (wanted == null) {
                continue;
            }
            long want = crafting.getRequestedAmount(key);
            if (want > 0L) {
                SccAe2Delivery.deliver(server, inventory, view, this.be, item, wanted, want);
            }
        }
    }
}
