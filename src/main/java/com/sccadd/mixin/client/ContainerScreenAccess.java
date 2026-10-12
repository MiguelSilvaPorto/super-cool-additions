package com.sccadd.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Position and size of a container screen, for panels drawn beside it. */
@Mixin(value = AbstractContainerScreen.class, remap = false)
public interface ContainerScreenAccess {
    @Accessor("leftPos")
    int sccadd$left();

    @Accessor("topPos")
    int sccadd$top();

    @Accessor("imageWidth")
    int sccadd$width();
}
