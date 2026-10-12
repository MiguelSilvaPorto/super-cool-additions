package com.sccadd.machine;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Implemented onto the Processing Calculator: the machine it is told to be, whatever stands in front of it. */
public interface MachineForce {
    @Nullable
    ResourceLocation sccadd$forced();

    /** {@code null} goes back to detecting the block in front. */
    void sccadd$setForced(@Nullable ResourceLocation machine);
}
