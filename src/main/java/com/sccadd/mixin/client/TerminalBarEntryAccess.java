package com.sccadd.mixin.client;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the id and title of a terminal bar entry (package-private in SCC). */
@Mixin(targets = "de.scc.calculator.client.screen.TerminalBar$Entry", remap = false)
public interface TerminalBarEntryAccess {
    @Accessor("id")
    String sccadd$id();

    @Accessor("title")
    Component sccadd$title();
}
