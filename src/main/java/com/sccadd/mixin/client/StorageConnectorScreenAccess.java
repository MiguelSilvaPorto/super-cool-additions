package com.sccadd.mixin.client;

import de.scc.calculator.client.screen.StorageConnectorScreen;
import de.scc.calculator.network.StorageConnectorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = StorageConnectorScreen.class, remap = false)
public interface StorageConnectorScreenAccess {
    @Invoker("connector")
    StorageConnectorBlockEntity sccadd$connector();
}
