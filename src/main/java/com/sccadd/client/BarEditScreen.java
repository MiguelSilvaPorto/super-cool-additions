package com.sccadd.client;

import com.sccadd.mixin.client.TerminalBarEntryAccess;
import de.scc.calculator.SccConfig;
import de.scc.calculator.client.screen.TerminalBar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Edits the buttons on the left of the SCC terminal: pin or unpin each one and
 * change the order. Saves into SCC's own bar setting, so it behaves exactly like
 * SCC's right-click pinning.
 */
public final class BarEditScreen extends Screen {
    public static final String EDIT_ID = "sccadd_bar";
    public static final String VARIANTS_ID = "sccadd_variants";
    private static final int ROWS = 11;
    private static final Map<String, Component> KNOWN = new LinkedHashMap<>();

    @Nullable
    private final Screen parent;
    private final List<String> pinned = new ArrayList<>();
    private int scroll;

    public BarEditScreen(@Nullable Screen parent) {
        super(Component.literal("Edit terminal bar"));
        this.parent = parent;
        List<String> now = SccConfig.get().terminalBar;
        this.pinned.addAll(now == null ? TerminalBar.DEFAULT : now);
    }

    public static void remember(List<TerminalBar.Entry> all) {
        KNOWN.clear();
        for (TerminalBar.Entry entry : all) {
            TerminalBarEntryAccess access = (TerminalBarEntryAccess) (Object) entry;
            KNOWN.put(access.sccadd$id(), access.sccadd$title());
        }
    }

    /** Pinned ones first, in their order, then everything else. */
    private List<String> rows() {
        List<String> out = new ArrayList<>();
        for (String id : this.pinned) {
            if (KNOWN.containsKey(id) && !out.contains(id)) {
                out.add(id);
            }
        }
        for (String id : KNOWN.keySet()) {
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        List<String> rows = this.rows();
        int maxScroll = Math.max(0, rows.size() - ROWS);
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));
        int left = this.width / 2 - 130;
        int top = 36;
        for (int i = 0; i < ROWS && this.scroll + i < rows.size(); i++) {
            String id = rows.get(this.scroll + i);
            int y = top + i * 22;
            boolean on = this.pinned.contains(id);
            boolean locked = EDIT_ID.equals(id);
            Button toggle = Button.builder(Component.literal(on ? "Pinned" : "Menu"), button -> {
                if (on) {
                    this.pinned.remove(id);
                } else {
                    this.pinned.add(id);
                }
                this.save();
            }).bounds(left + 170, y, 50, 20).build();
            toggle.active = !locked;
            this.addRenderableWidget(toggle);
            Button up = Button.builder(Component.literal("^"), button -> this.move(id, -1)).bounds(left + 224, y, 16, 20).build();
            Button down = Button.builder(Component.literal("v"), button -> this.move(id, 1)).bounds(left + 242, y, 16, 20).build();
            up.active = on && !locked;
            down.active = on && !locked;
            this.addRenderableWidget(up);
            this.addRenderableWidget(down);
        }
        this.addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
            this.pinned.clear();
            this.pinned.add(EDIT_ID);
            this.pinned.addAll(TerminalBar.DEFAULT);
            this.save();
        }).bounds(this.width / 2 - 104, this.height - 28, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(this.width / 2 + 4, this.height - 28, 100, 20).build());
    }

    private void move(String id, int by) {
        int at = this.pinned.indexOf(id);
        int to = at + by;
        // The editor itself keeps slot 0.
        if (at < 0 || to < 1 || to >= this.pinned.size()) {
            return;
        }
        this.pinned.remove(at);
        this.pinned.add(to, id);
        this.save();
    }

    private void save() {
        this.pinned.remove(EDIT_ID);
        this.pinned.add(0, EDIT_ID);
        SccConfig.get().terminalBar = new ArrayList<>(this.pinned);
        SccConfig.save();
        this.init();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        this.scroll -= (int) Math.signum(dy);
        this.init();
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("Pinned buttons sit on the bar, the rest are in the ... menu. Scroll for more."),
                this.width / 2, 24, 0xAAAAAA);
        List<String> rows = this.rows();
        int left = this.width / 2 - 130;
        for (int i = 0; i < ROWS && this.scroll + i < rows.size(); i++) {
            String id = rows.get(this.scroll + i);
            Component title = KNOWN.get(id);
            graphics.drawString(this.font, title == null ? Component.literal(id) : title, left, 42 + i * 22,
                    this.pinned.contains(id) ? 0xFFFFFF : 0x888888);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
