package com.sccadd.client;

import com.sccadd.mixin.client.ContainerScreenAccess;
import com.sccadd.mixin.client.TerminalBarAccess;
import de.scc.calculator.SccConfig;
import de.scc.calculator.client.WirelessClient;
import de.scc.calculator.client.screen.CraftingTerminalScreen;
import de.scc.calculator.client.screen.NetworkTerminalScreen;
import de.scc.calculator.client.screen.RestockScreen;
import de.scc.calculator.client.screen.SccGui;
import de.scc.calculator.network.CraftingTerminalMenu;
import de.scc.calculator.network.RestockConfig;
import de.scc.calculator.network.WirelessToolPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Right-hand panel of the pocket (wireless) terminal: bigger buttons for features SCC
 * already has but keeps tucked away in the bar's radio menu - magnet, restock and its
 * filters, pick, pick-and-order and the controller.
 */
public final class PocketPanel {
    private static final int SIZE = 22;
    private static final int GAP = 3;

    private record Button(String glyph, Component title, Component help, BooleanSupplier on, Runnable action) {
    }

    private PocketPanel() {
    }

    private static List<Button> buttons(NetworkTerminalScreen screen, Runnable refresh) {
        List<Button> out = new ArrayList<>();
        ItemStack held = WirelessClient.heldTerminal();
        RestockConfig restock = RestockConfig.of(held);
        SccConfig cfg = SccConfig.get();
        boolean crafting = screen.getMenu() instanceof CraftingTerminalMenu;
        out.add(new Button("magnet", Component.translatable("scc.gui.restock.magnet"),
                Component.translatable("scc.gui.terminal.magnet.tip"), restock::magnet, () -> {
            PacketDistributor.sendToServer(new WirelessToolPayload(1));
            ItemStack terminal = WirelessClient.heldTerminal();
            if (!terminal.isEmpty()) {
                RestockConfig now = RestockConfig.of(terminal);
                now.withMagnet(!now.magnet(), now.magnetBlacklist(), now.magnetFilter()).saveTo(terminal);
            }
            refresh.run();
        }));
        out.add(new Button("bag", Component.translatable("scc.gui.restock.title"),
                Component.translatable("scc.gui.terminal.keep.tip"), restock::restocks, () -> {
            RestockScreen.returnToTerminal(crafting);
            PacketDistributor.sendToServer(new WirelessToolPayload(0));
        }));
        out.add(new Button("mouse", Component.translatable("scc.gui.terminal.bar.pick"),
                Component.translatable("scc.gui.terminal.pick.tip"), () -> cfg.wirelessPick, () -> {
            cfg.wirelessPick = !cfg.wirelessPick;
            SccConfig.save();
            refresh.run();
        }));
        out.add(new Button("mouse_plus", Component.translatable("scc.gui.terminal.bar.pickorder"),
                Component.translatable("scc.gui.terminal.pickorder.tip"), () -> cfg.wirelessPick && cfg.wirelessPickOrder, () -> {
            cfg.wirelessPickOrder = !cfg.wirelessPickOrder;
            SccConfig.save();
            refresh.run();
        }));
        // Down arrow = export (system -> inventory); up arrow = import (inventory -> system).
        out.add(new Button("arrow_down", Component.literal("Export"),
                Component.literal("Choose, slot by slot, what the system keeps in your inventory: items, fluids in buckets and tanks, and whether to craft more when it runs out."),
                () -> hasRules(false), () -> Minecraft.getInstance().setScreen(
                        new SlotRulesScreen(Minecraft.getInstance().screen, SlotRulesScreen.Mode.EXPORT))));
        out.add(new Button("arrow_up", Component.literal("Import"),
                Component.literal("Choose, slot by slot, what goes into the system: the items, the fluid inside a bucket or tank, or both."),
                () -> hasRules(true), () -> Minecraft.getInstance().setScreen(
                        new SlotRulesScreen(Minecraft.getInstance().screen, SlotRulesScreen.Mode.IMPORT))));
        out.add(new Button("controller", Component.translatable("scc.gui.terminal.controller"),
                Component.translatable("scc.gui.terminal.controller.tip"), () -> false, () ->
                PacketDistributor.sendToServer(new WirelessToolPayload(crafting ? 3 : 2))));
        return out;
    }

    private static boolean hasRules(boolean imports) {
        for (com.sccadd.slots.SlotRule rule : com.sccadd.slots.SlotRules.of(WirelessClient.heldTerminal())) {
            if (rule.kind().isImport() == imports) {
                return true;
            }
        }
        return false;
    }

    private static void arrow(GuiGraphics g, int x, int y, boolean down, int color) {
        int cx = x + SIZE / 2;
        int top = y + 5;
        // Shaft and head, drawn from rectangles so no texture is needed.
        if (down) {
            g.fill(cx - 1, top, cx + 1, top + 8, color);
            for (int i = 0; i < 4; i++) {
                g.fill(cx - 4 + i, top + 8 - 1 + i, cx + 4 - i, top + 8 + i, color);
            }
        } else {
            g.fill(cx - 1, top + 4, cx + 1, top + 12, color);
            for (int i = 0; i < 4; i++) {
                g.fill(cx - 4 + i, top + 4 - i, cx + 4 - i, top + 5 - i, color);
            }
        }
    }

    private static int x(NetworkTerminalScreen screen) {
        ContainerScreenAccess access = (ContainerScreenAccess) (Object) screen;
        return access.sccadd$left() + access.sccadd$width() + 4;
    }

    private static int y(NetworkTerminalScreen screen) {
        return ((ContainerScreenAccess) (Object) screen).sccadd$top() + 20;
    }

    /** Only the pocket terminals (wireless terminal and wireless crafting terminal) get the panel. */
    public static boolean applies(NetworkTerminalScreen screen) {
        return ((com.sccadd.mixin.client.TerminalWirelessInvoker) (Object) screen).sccadd$wireless()
                && !WirelessClient.heldTerminal().isEmpty();
    }

    /** The screen area the panel covers, so JEI and others can stay clear of it. */
    public static net.minecraft.client.renderer.Rect2i area(NetworkTerminalScreen screen) {
        if (!applies(screen)) {
            return null;
        }
        int count = buttons(screen, () -> { }).size();
        return new net.minecraft.client.renderer.Rect2i(x(screen) - 3, y(screen) - 3, SIZE + 6, count * (SIZE + GAP) + 4);
    }

    public static void render(GuiGraphics g, int mx, int my, NetworkTerminalScreen screen) {
        if (!applies(screen)) {
            return;
        }
        List<Button> buttons = buttons(screen, () -> { });
        int x = x(screen);
        int y = y(screen);
        Minecraft minecraft = Minecraft.getInstance();
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        g.fill(x - 3, y - 3, x + SIZE + 3, y + buttons.size() * (SIZE + GAP) + 1, SccGui.PANEL);
        List<Component> tip = null;
        for (int i = 0; i < buttons.size(); i++) {
            Button button = buttons.get(i);
            int by = y + i * (SIZE + GAP);
            boolean over = mx >= x && mx < x + SIZE && my >= by && my < by + SIZE;
            boolean on = button.on().getAsBoolean();
            g.fill(x, by, x + SIZE, by + SIZE, over ? SccGui.ROW_HOVER : SccGui.PLATE_BG);
            SccGui.outline(g, x, by, SIZE, SIZE, on ? SccGui.ACCENT : SccGui.BORDER);
            int color = on ? SccGui.ACCENT : SccGui.TEXT;
            if (button.glyph().startsWith("arrow")) {
                arrow(g, x, by, "arrow_down".equals(button.glyph()), color);
            } else {
                SccGui.glyph(g, button.glyph(), x + (SIZE - 14) / 2, by + (SIZE - 14) / 2, 14, 14, color);
            }
            if (over) {
                tip = List.of(button.title(), button.help());
            }
        }
        if (tip != null) {
            SccGui.tooltip(g, minecraft.font, tip, mx, my);
        }
        g.pose().popPose();
    }

    /** True when the click landed on one of the panel's buttons. */
    public static boolean click(double mx, double my, int mouseButton, NetworkTerminalScreen screen) {
        if (mouseButton != 0 || !applies(screen)) {
            return false;
        }
        Runnable refresh = ((TerminalBarAccess) (Object) screen.bar).sccadd$rebuild();
        List<Button> buttons = buttons(screen, refresh);
        int x = x(screen);
        int y = y(screen);
        for (int i = 0; i < buttons.size(); i++) {
            int by = y + i * (SIZE + GAP);
            if (mx >= x && mx < x + SIZE && my >= by && my < by + SIZE) {
                net.minecraft.client.resources.sounds.SimpleSoundInstance click =
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F);
                Minecraft.getInstance().getSoundManager().play(click);
                buttons.get(i).action().run();
                return true;
            }
        }
        return false;
    }
}
