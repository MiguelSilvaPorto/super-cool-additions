package com.sccadd.mixin.client;

import de.scc.calculator.client.screen.SccGui;
import de.scc.calculator.network.TerminalBlockEntity;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SCC fades craftable-only entries by drawing them translucent, but block models ignore
 * that alpha, so some of them (terracotta, planks...) looked as bright as real stock.
 * A dark veil over every entry that is not in the system makes them all read as ghosts.
 */
@Mixin(targets = "de.scc.calculator.client.screen.ItemBatch", remap = false)
public abstract class ItemBatchGhostMixin {
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private static void sccadd$ghostVeil(GuiGraphics g, List<TerminalBlockEntity.Entry> entries, int start, int shown,
                                         int gx, int gy, int cols, CallbackInfo ci) {
        int veil = (0x78 << 24) | (SccGui.PANEL & 0xFFFFFF);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 190.0F);
        for (int i = 0; i < shown; i++) {
            TerminalBlockEntity.Entry entry = entries.get(start + i);
            if (!entry.isFluid() && !entry.isChemical() && !entry.isSource() && entry.count() <= 0L) {
                int x = gx + i % cols * 18 + 1;
                int y = gy + i / cols * 18 + 1;
                g.fill(x, y, x + 16, y + 16, veil);
            }
        }
        g.pose().popPose();
    }
}
