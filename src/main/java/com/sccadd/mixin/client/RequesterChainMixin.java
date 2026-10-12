package com.sccadd.mixin.client;

import com.sccadd.chain.ChainData;
import com.sccadd.chain.ChainHolder;
import com.sccadd.client.chain.ChainEditorScreen;
import de.scc.calculator.client.screen.RequesterScreen;
import de.scc.calculator.network.RequesterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Clicking a requester's empty-cursor item slot now offers two buttons side by side: pick a single
 * item (SCC's own picker) or draw a production chain for the row.
 */
@Mixin(value = RequesterScreen.class, remap = false)
public abstract class RequesterChainMixin {
    private static final int BTN_W = 44;
    private static final int BTN_H = 14;

    @Shadow
    protected abstract RequesterBlockEntity requester();

    @Shadow
    protected abstract void pickItem(int row);

    @Unique
    private int sccadd$choiceRow = -1;
    @Unique
    private boolean sccadd$bypass;

    @Unique
    private int sccadd$left() {
        return ((ContainerScreenAccess) (Object) this).sccadd$left();
    }

    @Unique
    private int sccadd$top() {
        return ((ContainerScreenAccess) (Object) this).sccadd$top();
    }

    @Unique
    private int sccadd$choiceX() {
        return this.sccadd$left() + 30;
    }

    @Unique
    private int sccadd$choiceY() {
        return this.sccadd$top() + 20 + this.sccadd$choiceRow * 16;
    }

    @Inject(method = "pickItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$choose(int row, CallbackInfo ci) {
        if (this.sccadd$bypass) {
            return;
        }
        if (((AbstractContainerScreen<?>) (Object) this).getMenu().getCarried().isEmpty()) {
            this.sccadd$choiceRow = row;
            ci.cancel();
        }
    }

    @Inject(method = "sccMouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$choiceClick(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.sccadd$choiceRow < 0) {
            return;
        }
        int row = this.sccadd$choiceRow;
        int x = this.sccadd$choiceX();
        int y = this.sccadd$choiceY();
        this.sccadd$choiceRow = -1;
        if (button != 0 || my < y || my >= y + BTN_H) {
            return;
        }
        if (mx >= x && mx < x + BTN_W) {
            this.sccadd$bypass = true;
            try {
                this.pickItem(row);
            } finally {
                this.sccadd$bypass = false;
            }
            cir.setReturnValue(true);
        } else if (mx >= x + BTN_W + 2 && mx < x + 2 * BTN_W + 2) {
            RequesterBlockEntity be = this.requester();
            if (be != null) {
                ChainEditorScreen.open((net.minecraft.client.gui.screens.Screen) (Object) this, be, row);
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "sccRender", at = @At("TAIL"), require = 0)
    private void sccadd$drawChoice(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        RequesterBlockEntity be = this.requester();
        if (be != null) {
            for (int i = 0; i < 6; i++) {
                ChainData chain = ((ChainHolder) be).sccadd$chain(i);
                if (chain != null) {
                    int x = this.sccadd$left() + 8;
                    int y = this.sccadd$top() + 20 + i * 16 - 1;
                    g.renderOutline(x, y, 18, 17, 0xFF4FD1BE);
                }
            }
        }
        if (this.sccadd$choiceRow < 0) {
            return;
        }
        int x = this.sccadd$choiceX();
        int y = this.sccadd$choiceY();
        var font = Minecraft.getInstance().font;
        String[] labels = {"1  Item", "2  Chain"};
        for (int i = 0; i < 2; i++) {
            int bx = x + i * (BTN_W + 2);
            boolean hover = mx >= bx && mx < bx + BTN_W && my >= y && my < y + BTN_H;
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            g.fill(bx, y, bx + BTN_W, y + BTN_H, 0xFF4FD1BE);
            g.fill(bx + 1, y + 1, bx + BTN_W - 1, y + BTN_H - 1, hover ? 0xFF2A3F44 : 0xFF161E22);
            g.drawString(font, labels[i], bx + 5, y + 3, 0xFFFFFFFF, false);
            g.pose().popPose();
        }
    }
}
