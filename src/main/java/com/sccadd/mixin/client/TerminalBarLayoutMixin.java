package com.sccadd.mixin.client;

import com.sccadd.SccAddConfig;
import com.sccadd.client.BarEditor;
import de.scc.calculator.SccConfig;
import de.scc.calculator.client.screen.TerminalBar;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the addon's own buttons to the SCC terminal bar: the bar editor (always the
 * first one, cannot be unpinned) and the variant filter.
 */
@Mixin(value = TerminalBar.class, remap = false)
public abstract class TerminalBarLayoutMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$editClick(double mx, double my, int button,
                                  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (button == 0 && BarEditor.ownsClick((TerminalBar) (Object) this, mx, my)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "layout", at = @At("HEAD"), require = 0)
    private void sccadd$addEntries(List<TerminalBar.Entry> all, net.minecraft.client.gui.Font font, int x, int top,
                                   int bottom, int screenW, int screenH, Runnable rebuild, CallbackInfo ci) {
        boolean hasEdit = false;
        boolean hasVariants = false;
        for (TerminalBar.Entry entry : all) {
            String id = ((TerminalBarEntryAccess) (Object) entry).sccadd$id();
            hasEdit |= BarEditor.EDIT_ID.equals(id);
            hasVariants |= BarEditor.VARIANTS_ID.equals(id);
        }
        if (!hasEdit) {
            all.add(0, TerminalBar.entry(BarEditor.EDIT_ID, "gear", Component.literal("Edit bar"), () -> {
                BarEditor.editing = !BarEditor.editing;
                rebuild.run();
            }).state(Component.literal(BarEditor.editing ? "On" : "Off")).selected(BarEditor.editing)
                    .help(Component.literal("Drag the buttons to reorder them. Drag from the tray on the left to pin, onto the tray to unpin.")));
        }
        if (!hasVariants) {
            boolean on = SccAddConfig.cleanVariants();
            all.add(TerminalBar.entry(BarEditor.VARIANTS_ID, "hidden", Component.literal("Clean variants"), () -> {
                SccAddConfig.setCleanVariants(!SccAddConfig.cleanVariants());
                de.scc.calculator.client.screen.TerminalList.invalidate();
                rebuild.run();
            }).state(Component.literal(on ? "On" : "Off")).selected(on)
                    .help(Component.literal("Hide craftable-only variants that carry enchantments, attributes, a custom name or lore.")));
        }

        boolean hasFilter = false;
        for (TerminalBar.Entry entry : all) {
            hasFilter |= BarEditor.FILTER_ID.equals(((TerminalBarEntryAccess) (Object) entry).sccadd$id());
        }
        if (!hasFilter) {
            boolean active = com.sccadd.client.AdvancedFilter.get().active();
            all.add(TerminalBar.entry(BarEditor.FILTER_ID, "list", Component.literal("Advanced filter"), () -> {
                net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                minecraft.setScreen(new com.sccadd.client.AdvancedFilterScreen(minecraft.screen));
            }).state(Component.literal(active ? "Active" : "Off")).selected(active)
                    .help(Component.literal("Choose exactly what the terminal list shows: stock, variants, mods, names.")));
        }

        // The editor is always the first pinned button.
        SccConfig config = SccConfig.get();
        List<String> pinned = config.terminalBar == null ? new ArrayList<>(TerminalBar.DEFAULT) : new ArrayList<>(config.terminalBar);
        pinned.remove(BarEditor.EDIT_ID);
        pinned.add(0, BarEditor.EDIT_ID);
        config.terminalBar = pinned;
    }
}
