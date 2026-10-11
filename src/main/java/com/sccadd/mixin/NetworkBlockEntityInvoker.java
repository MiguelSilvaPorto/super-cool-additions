package com.sccadd.mixin;

import de.scc.calculator.network.NetworkBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code NetworkBlockEntity.sync()} is protected and inherited, so it cannot be
 * shadowed from a mixin of the subclass. This invoker makes it reachable.
 */
@Mixin(value = NetworkBlockEntity.class, remap = false)
public interface NetworkBlockEntityInvoker {
    @Invoker("sync")
    void sccadd$sync();
}
