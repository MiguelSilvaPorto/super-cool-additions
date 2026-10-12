package com.sccadd.mixin;

import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.StorageConnectorBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** NetworkView.add(StorageConnectorBlockEntity) is package-private. */
@Mixin(value = NetworkView.class, remap = false)
public interface NetworkViewAdd {
    @Invoker("add")
    void sccadd$add(StorageConnectorBlockEntity storage);
}
