package com.sccadd.mixin.client;

import com.sccadd.think.ThinkQueue;
import de.scc.calculator.client.screen.JobsPanel;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Running jobs window also lists what is being thought about, in purple, at the top. Left click on
 * one that is done reopens the order with its plan ready; right click cancels or removes it.
 */
@Mixin(value = JobsPanel.class, remap = false)
public abstract class JobsPanelThinkMixin {
    private static final int ROW_HEIGHT = 30;

    @Shadow
    @Final
    private List<Object> entries;

    @Shadow
    private int x;

    @Shadow
    private int y;

    @Shadow
    private int scroll;

    @Inject(method = "finishRefresh", at = @At("TAIL"), require = 0)
    private void sccadd$addThinking(CallbackInfo ci) {
        List<ThinkQueue.Task> tasks = ThinkQueue.tasks();
        int at = 0;
        for (ThinkQueue.Task task : tasks) {
            Object entry = ThinkQueue.entryFor(task);
            if (entry != null) {
                this.entries.add(at++, entry);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$thinkingClick(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!((JobsPanel) (Object) this).isOpen() || mx < this.x + 5 || mx >= this.x + 259) {
            return;
        }
        List<ThinkQueue.Task> tasks = ThinkQueue.tasks();
        int top = this.y + 30 - this.scroll;
        int listTop = this.y + 30;
        int listBottom = this.y + 196 - 26;
        for (int i = 0; i < tasks.size(); i++) {
            int ty = top + i * ROW_HEIGHT;
            if (my >= ty && my < ty + ROW_HEIGHT - 4 && my >= listTop && my < listBottom) {
                ThinkQueue.Task task = tasks.get(i);
                if (button == 1) {
                    ThinkQueue.cancel(task);
                } else if (button == 0) {
                    ThinkQueue.resume(task);
                    ((JobsPanel) (Object) this).close();
                }
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
