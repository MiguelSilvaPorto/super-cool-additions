package com.sccadd.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the AE2 dependent mixins only when Applied Energistics 2 is present,
 * the same way SCC guards its own optional mixins.
 */
public final class SccAddMixinPlugin implements IMixinConfigPlugin {
    private Boolean ae2;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".Ae2")) {
            return this.ae2();
        }
        return true;
    }

    private boolean ae2() {
        if (this.ae2 == null) {
            boolean present;
            try {
                present = LoadingModList.get().getModFileById("ae2") != null;
            } catch (Throwable failed) {
                present = false;
            }
            this.ae2 = present;
        }
        return this.ae2;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
