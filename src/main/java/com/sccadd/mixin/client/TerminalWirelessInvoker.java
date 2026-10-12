package com.sccadd.mixin.client;

import de.scc.calculator.client.screen.NetworkTerminalScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** True for the pocket (wireless) terminals, false for the placed block terminals. */
@Mixin(value = NetworkTerminalScreen.class, remap = false)
public interface TerminalWirelessInvoker {
    @Invoker("wireless")
    boolean sccadd$wireless();
}
