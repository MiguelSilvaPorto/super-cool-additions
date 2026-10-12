package com.sccadd.mixin.client;

import com.sccadd.requester.BigMath;
import com.sccadd.requester.BigRow;
import com.sccadd.requester.RequesterTargetPayload;
import de.scc.calculator.client.screen.AmountMath;
import de.scc.calculator.client.screen.RequesterScreen;
import de.scc.calculator.client.screen.SccGui;
import de.scc.calculator.network.RequesterBlockEntity;
import de.scc.calculator.network.RequesterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The requester's two fields: what to keep in the system (typed, with formulas and k/m/g/t) and how
 * much to order each time (SCC's own typed field, now without the 9999 ceiling). Sent with
 * {@link RequesterTargetPayload}, because SCC's menu buttons only carry four digits.
 */
@Mixin(value = RequesterScreen.class, remap = false)
public abstract class RequesterScreenMixin {
    private static final int TARGET_X = 92;
    private static final int TARGET_W = 54;
    private static final int TIME_X = 147;
    private static final int TIME_W = 46;

    @Shadow
    private int batchFocus;

    @Shadow
    private String batchText;

    @Shadow
    protected abstract RequesterBlockEntity requester();

    @Shadow
    protected abstract boolean rowEmpty(int row);

    @Unique
    private int sccadd$focus = -1;

    /** 0 = the target stock, 1 = the time between orders. */
    @Unique
    private int sccadd$field;

    @Unique
    private String sccadd$text = "";

    @Unique
    private int sccadd$left() {
        return ((ContainerScreenAccess) (Object) this).sccadd$left();
    }

    @Unique
    private int sccadd$top() {
        return ((ContainerScreenAccess) (Object) this).sccadd$top();
    }

    /** The row of a click on the target or time field (field 0 or 1 in {@code sccadd$field}), or -1. */
    @Unique
    private int sccadd$rowAt(double mx, double my) {
        for (int i = 0; i < 6; i++) {
            int y = this.sccadd$top() + 20 + i * 16;
            if (my >= y && my < y + 16 && !this.rowEmpty(i)) {
                if (mx >= this.sccadd$left() + TARGET_X && mx < this.sccadd$left() + TARGET_X + TARGET_W) {
                    this.sccadd$hitField = 0;
                    return i;
                }
                if (mx >= this.sccadd$left() + TIME_X && mx < this.sccadd$left() + TIME_X + TIME_W) {
                    this.sccadd$hitField = 1;
                    return i;
                }
            }
        }
        return -1;
    }

    @Unique
    private int sccadd$hitField;

    @Unique
    private long sccadd$target(RequesterBlockEntity.Row row) {
        return ((BigRow) (Object) row).sccadd$bigAmount();
    }

    @Unique
    private void sccadd$send(int row, long amount, long batch) {
        this.sccadd$send(row, amount, batch, -1L);
    }

    @Unique
    private void sccadd$send(int row, long amount, long batch, long interval) {
        PacketDistributor.sendToServer(new RequesterTargetPayload(((RequesterMenu) ((RequesterScreen) (Object) this).getMenu()).pos(), row, amount, batch, interval));
    }

    @Unique
    private void sccadd$commit() {
        if (this.sccadd$focus < 0) {
            return;
        }
        int row = this.sccadd$focus;
        String text = this.sccadd$text;
        this.sccadd$focus = -1;
        this.sccadd$text = "";
        if (this.sccadd$field == 1) {
            long seconds = BigMath.evalSeconds(text);
            if (seconds != BigMath.INVALID) {
                this.sccadd$send(row, -1L, -1L, Math.max(1L, Math.min(RequesterTargetPayload.MAX_SECONDS, seconds)));
            }
            return;
        }
        long value = BigMath.eval(text);
        if (value != BigMath.INVALID) {
            this.sccadd$send(row, Math.max(1L, Math.min(BigMath.MAX, value)), -1L);
        }
    }

    // ---- the order size: SCC's own field, same behaviour, bigger numbers, sent with the new payload ----

    @Inject(method = "commitBatch", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$commitBatch(CallbackInfo ci) {
        ci.cancel();
        if (this.batchFocus < 0) {
            return;
        }
        int row = this.batchFocus;
        String text = this.batchText.trim();
        this.batchFocus = -1;
        this.batchText = "";
        long value = 0L;
        if (!text.isEmpty()) {
            long parsed = BigMath.eval(text);
            if (parsed == BigMath.INVALID) {
                return;
            }
            value = Math.max(0L, Math.min(RequesterTargetPayload.INT_CAP, parsed));
        }
        this.sccadd$send(row, -1L, value);
    }

    @ModifyConstant(method = "sccRender", constant = @Constant(longValue = 9999L), require = 0)
    private long sccadd$hintMax(long original) {
        return RequesterTargetPayload.INT_CAP;
    }

    @ModifyConstant(method = "charTyped", constant = @Constant(intValue = 4), require = 0)
    private int sccadd$batchDigits(int original) {
        return 24;
    }

    @Inject(method = "batchLabel", at = @At("RETURN"), cancellable = true, require = 0)
    private void sccadd$batchLabel(int row, CallbackInfoReturnable<String> cir) {
        RequesterBlockEntity be = this.requester();
        int size = be == null ? 0 : be.row(row).batchSize();
        if (size >= 10_000) {
            cir.setReturnValue("x" + BigMath.compact(size));
        }
    }

    // ---- the target: typed, formulas, k/m/g/t ----

    @Inject(method = "sccMouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$click(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        int row = this.sccadd$rowAt(mx, my);
        if (row >= 0) {
            if (button == 0 || button == 2) {
                RequesterBlockEntity be = this.requester();
                if (be != null) {
                    int field = this.sccadd$hitField;
                    this.sccadd$commit();
                    this.sccadd$focus = row;
                    this.sccadd$field = field;
                    this.sccadd$text = field == 1
                            ? Long.toString(be.row(row).intervalSeconds())
                            : Long.toString(this.sccadd$target(be.row(row)));
                    this.batchFocus = -1;
                    this.batchText = "";
                }
            }
            cir.setReturnValue(true);
        } else if (this.sccadd$focus >= 0) {
            this.sccadd$commit();
        }
    }

    @Inject(method = "sccMouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$scroll(double mx, double my, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        int row = this.sccadd$rowAt(mx, my);
        RequesterBlockEntity be = this.requester();
        if (row >= 0 && this.sccadd$hitField == 0 && dy != 0.0 && be != null) {
            long step = AmountMath.wheelStep(Screen_hasShift(), Screen_hasControl(), false);
            long now = this.sccadd$target(be.row(row));
            this.sccadd$send(row, Math.max(1L, now + (dy > 0.0 ? step : -step)), -1L);
            cir.setReturnValue(true);
        } else if (row >= 0) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static boolean Screen_hasShift() {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }

    @Unique
    private static boolean Screen_hasControl() {
        return net.minecraft.client.gui.screens.Screen.hasControlDown();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$typed(char ch, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (this.sccadd$focus >= 0) {
            boolean ok = this.sccadd$field == 1 ? BigMath.allowedTimeChar(ch) : BigMath.allowedChar(ch);
            if (ok && this.sccadd$text.length() < 40) {
                this.sccadd$text = this.sccadd$text + ch;
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$key(int key, int scan, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (this.sccadd$focus < 0) {
            return;
        }
        if (key == 259) { // backspace
            if (!this.sccadd$text.isEmpty()) {
                this.sccadd$text = this.sccadd$text.substring(0, this.sccadd$text.length() - 1);
            }
        } else if (key == 257 || key == 335) { // enter
            this.sccadd$commit();
        } else if (key == 256) { // escape
            this.sccadd$focus = -1;
            this.sccadd$text = "";
        }
        cir.setReturnValue(true); // the field owns the keys, so "e" does not close the screen
    }

    @Inject(method = "sccRender", at = @At("TAIL"), require = 0)
    private void sccadd$drawFields(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        RequesterBlockEntity be = this.requester();
        if (be == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int left = this.sccadd$left();
        int top = this.sccadd$top();
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 250.0F);
        for (int i = 0; i < 6; i++) {
            if (this.rowEmpty(i)) {
                continue;
            }
            RequesterBlockEntity.Row row = be.row(i);
            int y = top + 20 + i * 16;
            long target = this.sccadd$target(row);
            boolean typing = this.sccadd$focus == i;
            // Cover SCC's "have / target" text, its "-  target  +" and "<  every  >" controls, and draw ours.
            int[][] covers = {{27, 91}, {TARGET_X - 1, TARGET_X + TARGET_W + 1}, {TIME_X - 1, TIME_X + TIME_W + 1}};
            for (int[] cover : covers) {
                g.fill(left + cover[0], y, left + cover[1], y + 14, SccGui.BG);
                if (i % 2 == 1) {
                    g.fill(left + cover[0], y, left + cover[1], y + 14, SccGui.ROW_ALT);
                }
            }
            String have = SccGui.shortNumber(row.have) + " / " + BigMath.compact(target) + (row.fluid ? " B" : "");
            g.drawString(font, font.plainSubstrByWidth(have, 58), left + 28, y + 3, row.enabled ? SccGui.TEXT : SccGui.MUTED, false);
            if (row.enabled && ("missing".equals(row.state) || "norecipe".equals(row.state) || "jobfailed".equals(row.state))) {
                g.drawString(font, "!", left + 87, y + 3, SccGui.BAD, false);
            }
            boolean typingTarget = typing && this.sccadd$field == 0;
            boolean typingTime = typing && this.sccadd$field == 1;
            SccGui.textField(g, font, typingTarget ? this.sccadd$text : BigMath.compact(target), left + TARGET_X, y + 1, TARGET_W, 12, typingTarget, "-");
            SccGui.textField(g, font, typingTime ? this.sccadd$text : BigMath.compactTime(row.intervalSeconds()), left + TIME_X, y + 1, TIME_W, 12, typingTime, "-");
            if (typingTarget) {
                Component hint = AmountMath.hint(this.sccadd$text, 1L, BigMath.MAX);
                if (hint != null) {
                    int w = font.width(hint);
                    g.fill(left + TARGET_X - w - 6, y + 1, left + TARGET_X - 1, y + 13, SccGui.BG);
                    AmountMath.drawHint(g, font, this.sccadd$text, 1L, BigMath.MAX, left + TARGET_X - 3, y + 4);
                }
            } else if (typingTime) {
                long seconds = BigMath.evalSeconds(this.sccadd$text);
                if (seconds != BigMath.INVALID && !this.sccadd$text.trim().matches("\\d+")) {
                    String text = "= " + BigMath.compactTime(Math.max(1L, seconds));
                    int w = font.width(text);
                    g.fill(left + TIME_X - w - 6, y + 1, left + TIME_X - 1, y + 13, SccGui.BG);
                    g.drawString(font, text, left + TIME_X - w - 3, y + 4, SccGui.ACCENT, false);
                }
            }
        }
        g.pose().popPose();
    }
}
