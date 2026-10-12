package com.sccadd.mixin;

import de.scc.calculator.network.StorageConnectorBlockEntity;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reaches the connector's private fluid filter from the mixin. */
@Mixin(value = StorageConnectorBlockEntity.class, remap = false)
public interface ConnectorAllowsInvoker {
    @Invoker("allows")
    boolean sccadd$connectorAllows(Fluid fluid);
}
