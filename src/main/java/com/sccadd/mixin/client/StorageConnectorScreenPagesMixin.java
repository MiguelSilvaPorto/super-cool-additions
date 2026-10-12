package com.sccadd.mixin.client;

import com.sccadd.iface.FilterWindow;
import de.scc.calculator.client.screen.SccGui;
import de.scc.calculator.client.screen.StorageConnectorScreen;
import de.scc.calculator.network.StorageConnectorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Storage Connector's filter has seven pages of nine slots. The screen keeps drawing and editing its one row;
 * the filter shifts that row to the page chosen here, with arrows in the header (or Page Up / Page Down).
 */
@Mixin(value = StorageConnectorScreen.class, remap = false)
public abstract class StorageConnectorScreenPagesMixin {
    @Shadow
    protected abstract StorageConnectorBlockEntity connector();

    private FilterWindow sccadd$window() {
        StorageConnectorBlockEntity be = this.connector();
        return be == null ? null : (FilterWindow) (Object) be.filter();
    }

    private int sccadd$pages(FilterWindow window) {
        return Math.max(1, window.sccadd$capacity() / FilterWindow.SCC_PAGE);
    }

    private int sccadd$arrowX(boolean next) {
        int left = ((ContainerScreenAccess) this).sccadd$left();
        int width = ((ContainerScreenAccess) this).sccadd$width();
        return left + width - (next ? 14 : 62);
    }

    private int sccadd$arrowY() {
        return ((ContainerScreenAccess) this).sccadd$top() + 3;
    }

    private void sccadd$turn(FilterWindow window, int by) {
        int pages = this.sccadd$pages(window);
        int page = Math.floorMod(window.sccadd$base() / FilterWindow.SCC_PAGE + by, pages);
        window.sccadd$setBase(page * FilterWindow.SCC_PAGE);
    }

    @Inject(method = "init", at = @At("HEAD"), require = 0)
    private void sccadd$firstPage(CallbackInfo ci) {
        FilterWindow window = this.sccadd$window();
        if (window != null) {
            window.sccadd$setBase(0);
        }
    }

    /** The row the screen edits is slot n of the page; the server is told the real slot. */
    @ModifyVariable(method = "sendFilter", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int sccadd$realSlot(int slot) {
        FilterWindow window = this.sccadd$window();
        return window == null ? slot : slot + window.sccadd$base();
    }

    @Inject(method = "sccRender", at = @At("TAIL"), require = 0)
    private void sccadd$pager(GuiGraphics g, int mx, int my, float partial, CallbackInfo ci) {
        FilterWindow window = this.sccadd$window();
        if (window == null || window.sccadd$capacity() <= FilterWindow.SCC_PAGE) {
            return;
        }
        var font = Minecraft.getInstance().font;
        int y = this.sccadd$arrowY();
        int page = window.sccadd$base() / FilterWindow.SCC_PAGE + 1;
        int pages = this.sccadd$pages(window);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        boolean back = mx >= this.sccadd$arrowX(false) && mx < this.sccadd$arrowX(false) + 10 && my >= y && my < y + 10;
        boolean next = mx >= this.sccadd$arrowX(true) && mx < this.sccadd$arrowX(true) + 10 && my >= y && my < y + 10;
        g.drawString(font, "<", this.sccadd$arrowX(false) + 2, y + 1, back ? SccGui.ACCENT : SccGui.TEXT, false);
        String label = "Page " + page + "/" + pages;
        g.drawString(font, label, this.sccadd$arrowX(false) + 13, y + 1, SccGui.MUTED, false);
        g.drawString(font, ">", this.sccadd$arrowX(true) + 2, y + 1, next ? SccGui.ACCENT : SccGui.TEXT, false);
        if (back || next) {
            SccGui.tooltip(g, font, java.util.List.of(Component.literal("Filter page " + page + " of " + pages
                    + " (" + window.sccadd$capacity() + " slots)"), Component.literal("Page Up / Page Down also turns it")), mx, my);
        }
        g.pose().popPose();
    }

    @Inject(method = "sccMouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$pagerClick(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        FilterWindow window = this.sccadd$window();
        if (window == null || window.sccadd$capacity() <= FilterWindow.SCC_PAGE || button != 0) {
            return;
        }
        int y = this.sccadd$arrowY();
        if (my >= y && my < y + 10) {
            if (mx >= this.sccadd$arrowX(false) && mx < this.sccadd$arrowX(false) + 10) {
                this.sccadd$turn(window, -1);
                cir.setReturnValue(true);
            } else if (mx >= this.sccadd$arrowX(true) && mx < this.sccadd$arrowX(true) + 10) {
                this.sccadd$turn(window, 1);
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$pageKeys(int key, int scan, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        FilterWindow window = this.sccadd$window();
        if (window == null || window.sccadd$capacity() <= FilterWindow.SCC_PAGE) {
            return;
        }
        if (key == 266) {
            this.sccadd$turn(window, -1);
            cir.setReturnValue(true);
        } else if (key == 267) {
            this.sccadd$turn(window, 1);
            cir.setReturnValue(true);
        }
    }
}
