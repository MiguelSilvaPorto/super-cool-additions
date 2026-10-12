package com.sccadd.mixin.client;

import com.sccadd.SccAddConfig;
import com.sccadd.think.ThinkQueue;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.client.screen.OrderDialog;
import de.scc.calculator.client.screen.SccButton;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The order dialog's fourth bottom button, "Think": the plan is computed in the background and the order
 * waits for you in the Autocraft list. Also lifts the 9999 ceiling of the amount field.
 */
@Mixin(value = OrderDialog.class, remap = false)
public abstract class OrderDialogThinkMixin {
    @Shadow
    private StackKey item;

    @Shadow
    private Supplier<Map<StackKey, Long>> stock;

    @Shadow
    @Final
    private List<StackKey> variants;

    @Shadow
    private boolean pickPending;

    @Shadow
    private int x;

    @Shadow
    private int y;

    @Shadow
    private int height;

    @Shadow
    @Final
    private List<SccButton> buttons;

    @Shadow
    protected abstract long parseAmount();

    @Shadow
    private static boolean reuseWanted() {
        throw new AssertionError();
    }

    @Shadow
    public abstract void close();

    @ModifyConstant(method = "maxAmount", constant = @Constant(intValue = 9999), require = 0)
    private int sccadd$maxAmount(int original) {
        return SccAddConfig.orderMaxAmount();
    }

    @Inject(method = "buildButtons", at = @At("TAIL"), require = 0)
    private void sccadd$thinkButton(CallbackInfo ci) {
        int row = this.y + this.height - 22;
        List<SccButton> bottom = new ArrayList<>();
        for (SccButton button : this.buttons) {
            if (button.y == row && button.h == 16) {
                bottom.add(button);
            }
        }
        // Confirm, cancel and the plan list; the "Back" screen has a single button there.
        if (bottom.size() != 3) {
            return;
        }
        bottom.sort(Comparator.comparingInt(button -> button.x));
        bottom.get(0).bounds(this.x + 8, row, 70, 16);
        bottom.get(1).bounds(this.x + 82, row, 46, 16);
        SccButton think = new SccButton("", () -> this.sccadd$think())
                .bounds(this.x + 132, row, 40, 16)
                .tooltip("Think: plan this order in the background. Keep playing; it waits in the Autocraft list.");
        think.glyph("clock");
        bottom.get(2).bounds(this.x + 176, row, 46, 16);
        this.buttons.add(think);
    }

    @Unique
    private void sccadd$think() {
        if (this.item == null || this.stock == null) {
            return;
        }
        long amount = Math.max(1L, this.parseAmount());
        if (ThinkQueue.start(this.item, amount, this.stock.get(), List.copyOf(this.variants), this.pickPending, reuseWanted())) {
            this.close();
        }
    }
}
