package com.sccadd.mixin;

import appeng.api.storage.MEStorage;
import de.scc.calculator.compat.ae2.Ae2MeStore;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Builds SCC's own ME item handler around a grid inventory (its constructor is private). */
@Mixin(value = Ae2MeStore.class, remap = false)
public interface Ae2MeStoreInvoker {
    @Invoker("<init>")
    static Ae2MeStore sccadd$create(MEStorage storage, Level level) {
        throw new AssertionError();
    }
}
