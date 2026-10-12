package com.sccadd.mixin.client;

import com.sccadd.think.ThinkQueue;
import de.scc.calculator.client.screen.JobStrip;
import de.scc.calculator.client.screen.SccGui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Purple cards in the Autocraft strip for the plans being thought about; clicking opens the jobs list. */
@Mixin(value = JobStrip.class, remap = false)
public abstract class JobStripThinkMixin {
    @Shadow
    @Final
    private List<Object> jobs;

    @Shadow
    private int x;

    @Shadow
    private int y;

    @Unique
    private int sccadd$cell(int index, boolean horizontal) {
        return horizontal ? this.x + 2 + index % 3 * 18 : this.y + 11 + index / 3 * 18;
    }

    @Unique
    private int sccadd$thinkingAt(double mx, double my) {
        List<ThinkQueue.Task> tasks = ThinkQueue.tasks();
        for (int k = 0; k < tasks.size(); k++) {
            int index = this.jobs.size() + k;
            if (index >= 6) {
                break;
            }
            int cx = this.sccadd$cell(index, true);
            int cy = this.sccadd$cell(index, false);
            if (mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18) {
                return k;
            }
        }
        return -1;
    }

    @Inject(method = "isEmpty", at = @At("RETURN"), cancellable = true, require = 0)
    private void sccadd$notEmptyWhenThinking(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && ThinkQueue.hasTasks()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void sccadd$drawThinking(GuiGraphics g, Font font, int x, int y, int mouseX, int mouseY, CallbackInfo ci) {
        List<ThinkQueue.Task> tasks = ThinkQueue.tasks();
        long now = System.currentTimeMillis();
        for (int k = 0; k < tasks.size(); k++) {
            int index = this.jobs.size() + k;
            if (index >= 6) {
                break;
            }
            ThinkQueue.Task task = tasks.get(k);
            int cx = x + 2 + index % 3 * 18;
            int cy = y + 11 + index / 3 * 18;
            SccGui.slot(g, cx, cy);
            g.renderItem(task.item.display(), cx + 1, cy + 1);
            SccGui.count(g, font, cx, cy, SccGui.shortNumber(task.amount));
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 260.0F);
            int color = task.state == ThinkQueue.State.FAILED ? 0xFFE05555 : ThinkQueue.PURPLE;
            // A purple veil, pulsing while it thinks, solid when it is done.
            int alpha = task.state == ThinkQueue.State.THINKING ? 0x30 + (int) (0x30 * (0.5 + 0.5 * Math.sin(now / 250.0))) : 0x50;
            g.fill(cx + 1, cy + 1, cx + 17, cy + 17, (alpha << 24) | (color & 0xFFFFFF));
            g.fill(cx + 1, cy + 1, cx + 17, cy + 2, color);
            g.fill(cx + 1, cy + 15, cx + 17, cy + 17, color);
            g.pose().popPose();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$clickThinking(double mx, double my, CallbackInfoReturnable<Boolean> cir) {
        if (this.sccadd$thinkingAt(mx, my) >= 0) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$tipThinking(GuiGraphics g, Font font, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        int k = this.sccadd$thinkingAt(mouseX, mouseY);
        if (k < 0) {
            return;
        }
        ThinkQueue.Task task = ThinkQueue.tasks().get(k);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(task.amount + "x " + task.item.display().getHoverName().getString()));
        lines.add(Component.literal(switch (task.state) {
            case THINKING -> "Thinking... " + task.seconds() + " s";
            case READY -> "Ready - open the list to review and start it";
            case FAILED -> "Could not plan this";
        }));
        SccGui.tooltip(g, font, lines, mouseX, mouseY);
        cir.setReturnValue(true);
    }
}
