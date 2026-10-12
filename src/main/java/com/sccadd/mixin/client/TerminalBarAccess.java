package com.sccadd.mixin.client;

import de.scc.calculator.client.screen.SccButton;
import de.scc.calculator.client.screen.TerminalBar;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The bar's private layout state, for the drag editor. */
@Mixin(value = TerminalBar.class, remap = false)
public interface TerminalBarAccess {
    @Accessor("buttons")
    List<SccButton> sccadd$buttons();

    @Accessor("entryOf")
    Map<SccButton, TerminalBar.Entry> sccadd$entryOf();

    @Accessor("moreMenu")
    List<TerminalBar.Entry> sccadd$more();

    @Accessor("radioMenu")
    List<TerminalBar.Entry> sccadd$radio();

    @Accessor("rebuild")
    Runnable sccadd$rebuild();
}
