package com.sccadd.mixin.client;

import com.sccadd.client.BarEditor;
import de.scc.calculator.client.screen.NetworkTerminalScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs the bar drag editor once per frame while the terminal is open. */
@Mixin(value = NetworkTerminalScreen.class, remap = false)
public abstract class TerminalScreenBarEditMixin {
    @Inject(method = "renderCommonTooltips", at = @At("HEAD"), require = 0)
    private void sccadd$barEditFrame(GuiGraphics g, int mouseX, int mouseY, ItemStack hovered,
                                     CallbackInfoReturnable<Boolean> cir) {
        BarEditor.frame(g, mouseX, mouseY, ((NetworkTerminalScreen) (Object) this).bar);
    }
}
