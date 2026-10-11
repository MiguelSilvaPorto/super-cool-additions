package com.sccadd.mixin;

import com.sccadd.SccAddConfig;
import com.sccadd.ae2.Ae2PatternCache;
import com.sccadd.ae2.Ae2PatternSync;
import de.scc.calculator.compat.ae2.Ae2Hooks;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.TerminalBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Collects AE2 pattern details on the server and rides them along with the
 * terminal's block entity sync (same mechanism SCC uses for its AE2 craftable
 * list). The client half lives in {@code client.TerminalAe2ClientMixin}.
 */
@Mixin(value = TerminalBlockEntity.class, remap = false)
public abstract class TerminalAe2PatternsMixin {
    @Unique
    private String sccadd$ae2Patterns = "";

    @Unique
    private String sccadd$ae2CraftablesSeen = "";

    @Inject(method = "collectAe2", at = @At("RETURN"), require = 0)
    private void sccadd$collectPatterns(NetworkView view, CallbackInfoReturnable<String> cir) {
        if (!SccAddConfig.ae2PatternsInScc() || !Ae2Hooks.loaded()) {
            return;
        }
        Level level = ((TerminalBlockEntity) (Object) this).getLevel();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        String craftables = cir.getReturnValue();
        if (craftables == null || craftables.equals(this.sccadd$ae2CraftablesSeen)) {
            return;
        }
        this.sccadd$ae2CraftablesSeen = craftables;
        this.sccadd$ae2Patterns = Ae2PatternSync.collect(server, view);
        Ae2PatternCache.put(this, this.sccadd$ae2Patterns);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 0)
    private void sccadd$savePatterns(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (!this.sccadd$ae2Patterns.isEmpty()) {
            tag.putString("sccaddAe2", this.sccadd$ae2Patterns);
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), require = 0)
    private void sccadd$loadPatterns(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.sccadd$ae2Patterns = tag.getString("sccaddAe2");
        Ae2PatternCache.put(this, this.sccadd$ae2Patterns);
    }

    @Unique
    public String sccadd$patternsText() {
        return this.sccadd$ae2Patterns;
    }
}
