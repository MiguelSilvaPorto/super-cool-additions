package com.sccadd.mixin.client;

import com.sccadd.SccAddConfig;
import com.sccadd.client.BarEditScreen;
import de.scc.calculator.SccConfig;
import de.scc.calculator.client.screen.TerminalBar;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
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
    @Inject(method = "layout", at = @At("HEAD"), require = 0)
    private void sccadd$addEntries(List<TerminalBar.Entry> all, net.minecraft.client.gui.Font font, int x, int top,
                                   int bottom, int screenW, int screenH, Runnable rebuild, CallbackInfo ci) {
        boolean hasEdit = false;
        boolean hasVariants = false;
        for (TerminalBar.Entry entry : all) {
            String id = ((TerminalBarEntryAccess) (Object) entry).sccadd$id();
            hasEdit |= BarEditScreen.EDIT_ID.equals(id);
            hasVariants |= BarEditScreen.VARIANTS_ID.equals(id);
        }
        if (!hasEdit) {
            all.add(0, TerminalBar.entry(BarEditScreen.EDIT_ID, "gear", Component.literal("Edit bar"), () -> {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.setScreen(new BarEditScreen(minecraft.screen));
            }).help(Component.literal("Choose which buttons sit on this bar, and in which order.")));
        }
        if (!hasVariants) {
            boolean on = SccAddConfig.cleanVariants();
            all.add(TerminalBar.entry(BarEditScreen.VARIANTS_ID, "hidden", Component.literal("Clean variants"), () -> {
                SccAddConfig.setCleanVariants(!SccAddConfig.cleanVariants());
                de.scc.calculator.client.screen.TerminalList.invalidate();
                rebuild.run();
            }).state(Component.literal(on ? "On" : "Off")).selected(on)
                    .help(Component.literal("Hide craftable-only variants that carry enchantments, attributes, a custom name or lore.")));
        }
        BarEditScreen.remember(all);

        // The editor is always the first pinned button.
        SccConfig config = SccConfig.get();
        List<String> pinned = config.terminalBar == null ? new ArrayList<>(TerminalBar.DEFAULT) : new ArrayList<>(config.terminalBar);
        pinned.remove(BarEditScreen.EDIT_ID);
        pinned.add(0, BarEditScreen.EDIT_ID);
        config.terminalBar = pinned;
    }
}
