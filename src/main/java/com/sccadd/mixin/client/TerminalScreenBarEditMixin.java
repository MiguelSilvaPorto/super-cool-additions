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
        NetworkTerminalScreen screen = (NetworkTerminalScreen) (Object) this;
        BarEditor.frame(g, mouseX, mouseY, screen.bar);
        com.sccadd.client.PocketPanel.render(g, mouseX, mouseY, screen);
    }

    @Inject(method = "handleCommonClick", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$pocketClick(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        if (com.sccadd.client.PocketPanel.click(mx, my, button, (NetworkTerminalScreen) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    /** SCC already tells JEI and EMI which areas to keep clear of; the pocket panel joins that list. */
    @Inject(method = "sccExtraAreas", at = @At("RETURN"), cancellable = true, require = 0)
    private void sccadd$pocketArea(CallbackInfoReturnable<java.util.List<net.minecraft.client.renderer.Rect2i>> cir) {
        net.minecraft.client.renderer.Rect2i area = com.sccadd.client.PocketPanel.area((NetworkTerminalScreen) (Object) this);
        if (area != null) {
            java.util.List<net.minecraft.client.renderer.Rect2i> out = new java.util.ArrayList<>(cir.getReturnValue());
            out.add(area);
            cir.setReturnValue(out);
        }
    }
}
