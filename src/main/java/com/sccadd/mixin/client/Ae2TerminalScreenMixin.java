package com.sccadd.mixin.client;

import com.sccadd.ae2.Ae2PatternCache;
import com.sccadd.ae2.client.Ae2PatternBook;
import de.scc.calculator.client.Ae2Book;
import de.scc.calculator.client.screen.NetworkTerminalScreen;
import de.scc.calculator.network.TerminalBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sets the detailed AE2 patterns exactly where SCC sets its own AE2 craftable
 * list: from the terminal the player is looking at. This keeps the pattern book
 * in sync with the viewed network instead of the last terminal that happened to
 * sync its data.
 */
@Mixin(value = NetworkTerminalScreen.class, remap = false)
public abstract class Ae2TerminalScreenMixin {
    @Shadow
    protected abstract TerminalBlockEntity terminal();

    @Redirect(
            method = "rebuild",
            at = @At(
                    value = "INVOKE",
                    target = "Lde/scc/calculator/client/Ae2Book;set(Ljava/lang/String;)V"
            ),
            require = 0
    )
    private void sccadd$setPatterns(String craftables) {
        Ae2Book.set(craftables);
        TerminalBlockEntity be = this.terminal();
        if (be != null) {
            Ae2PatternBook.set(Ae2PatternCache.get(be));
        }
    }
}
