package com.sccadd.client;

import com.sccadd.mixin.client.TerminalBarAccess;
import com.sccadd.mixin.client.TerminalBarEntryAccess;
import de.scc.calculator.SccConfig;
import de.scc.calculator.client.screen.SccButton;
import de.scc.calculator.client.screen.SccGui;
import de.scc.calculator.client.screen.TerminalBar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Drag editor for the SCC terminal bar. While on, the bar buttons can be dragged
 * to reorder them, and a tray to their left shows the buttons that are not pinned:
 * drag one onto the bar to pin it, drag a pinned one onto the tray to unpin it.
 * It saves into SCC's own bar setting, like the right-click pinning does.
 */
public final class BarEditor {
    public static final String EDIT_ID = "sccadd_bar";
    public static final String VARIANTS_ID = "sccadd_variants";
    public static final String FILTER_ID = "sccadd_filter";
    private static final int STEP = 15;
    private static final int CELL = 14;

    public static boolean editing;
    @Nullable
    private static String dragId;
    private static boolean dragFromBar;
    private static boolean wasDown;

    private BarEditor() {
    }

    private record Cell(String id, String glyph, Component title, int x, int y) {
        boolean over(double mx, double my) {
            return mx >= this.x && mx < this.x + CELL && my >= this.y && my < this.y + CELL;
        }
    }

    private static String id(TerminalBar.Entry entry) {
        return ((TerminalBarEntryAccess) (Object) entry).sccadd$id();
    }

    /** Pinned (draggable) buttons of the bar, top to bottom. */
    private static List<Cell> barCells(TerminalBarAccess bar) {
        List<Cell> out = new ArrayList<>();
        for (SccButton button : bar.sccadd$buttons()) {
            TerminalBar.Entry entry = bar.sccadd$entryOf().get(button);
            if (entry != null) {
                TerminalBarEntryAccess access = (TerminalBarEntryAccess) (Object) entry;
                out.add(new Cell(access.sccadd$id(), access.sccadd$glyph(), access.sccadd$title(), button.x, button.y));
            }
        }
        return out;
    }

    private static List<Cell> trayCells(TerminalBarAccess bar, int barX, int top, int bottom) {
        Map<String, TerminalBar.Entry> loose = new LinkedHashMap<>();
        for (TerminalBar.Entry entry : bar.sccadd$more()) {
            loose.putIfAbsent(id(entry), entry);
        }
        for (TerminalBar.Entry entry : bar.sccadd$radio()) {
            loose.putIfAbsent(id(entry), entry);
        }
        loose.remove(EDIT_ID);
        int rows = Math.max(1, (bottom - top) / STEP);
        List<Cell> out = new ArrayList<>();
        int index = 0;
        for (TerminalBar.Entry entry : loose.values()) {
            TerminalBarEntryAccess access = (TerminalBarEntryAccess) (Object) entry;
            int column = index / rows;
            int row = index % rows;
            out.add(new Cell(access.sccadd$id(), access.sccadd$glyph(), access.sccadd$title(),
                    barX - 20 - column * STEP, top + row * STEP));
            index++;
        }
        return out;
    }

    /** True when a plain click would hit a bar button that the editor wants to drag instead. */
    public static boolean ownsClick(TerminalBar bar, double mx, double my) {
        if (!editing) {
            return false;
        }
        for (Cell cell : barCells((TerminalBarAccess) (Object) bar)) {
            if (!EDIT_ID.equals(cell.id()) && cell.over(mx, my)) {
                return true;
            }
        }
        return false;
    }

    /** Called every frame by the terminal screen. */
    public static void frame(GuiGraphics g, int mx, int my, TerminalBar terminalBar) {
        if (!editing) {
            dragId = null;
            wasDown = false;
            return;
        }
        TerminalBarAccess bar = (TerminalBarAccess) (Object) terminalBar;
        List<Cell> pinned = barCells(bar);
        if (pinned.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int barX = pinned.get(0).x();
        int top = pinned.get(0).y();
        int bottom = minecraft.getWindow().getGuiScaledHeight() - 4;
        List<Cell> tray = trayCells(bar, barX, top, bottom);

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        // Tray background and title.
        if (!tray.isEmpty()) {
            int minX = Integer.MAX_VALUE;
            int maxY = top;
            for (Cell cell : tray) {
                minX = Math.min(minX, cell.x());
                maxY = Math.max(maxY, cell.y() + CELL);
            }
            g.fill(minX - 3, top - 12, barX - 4, maxY + 3, SccGui.PANEL);
            g.drawString(minecraft.font, "Not pinned", minX - 1, top - 10, SccGui.MUTED, false);
        } else {
            g.fill(barX - 62, top - 12, barX - 4, top + 12, SccGui.PANEL);
            g.drawString(minecraft.font, "All pinned", barX - 60, top - 8, SccGui.MUTED, false);
        }
        for (Cell cell : tray) {
            drawCell(g, cell, cell.over(mx, my), false);
        }
        for (Cell cell : pinned) {
            drawCell(g, cell, cell.over(mx, my) && !EDIT_ID.equals(cell.id()), EDIT_ID.equals(cell.id()));
        }

        boolean down = minecraft.mouseHandler.isLeftPressed();
        if (down && !wasDown && dragId == null) {
            for (Cell cell : pinned) {
                if (!EDIT_ID.equals(cell.id()) && cell.over(mx, my)) {
                    dragId = cell.id();
                    dragFromBar = true;
                }
            }
            for (Cell cell : tray) {
                if (cell.over(mx, my)) {
                    dragId = cell.id();
                    dragFromBar = false;
                }
            }
        }
        if (dragId != null && down) {
            int index = insertIndex(pinned, my, top);
            if (overBar(mx, barX)) {
                int y = top + index * STEP - 1;
                g.fill(barX - 2, y, barX + CELL + 2, y + 2, SccGui.ACCENT);
            }
            Cell source = find(pinned, dragId);
            if (source == null) {
                source = find(tray, dragId);
            }
            if (source != null) {
                g.fill(mx - 8, my - 8, mx + 8, my + 8, SccGui.PANEL);
                SccGui.glyph(g, source.glyph(), mx - 7, my - 7, CELL, CELL, SccGui.ACCENT);
            }
        }
        if (!down && wasDown && dragId != null) {
            drop(bar, pinned, mx, my, barX, top);
            dragId = null;
        }
        wasDown = down;

        // Hover tooltip for the cells.
        for (Cell cell : tray) {
            if (cell.over(mx, my) && dragId == null) {
                SccGui.tooltip(g, minecraft.font, List.of(cell.title()), mx, my);
            }
        }
        g.pose().popPose();
    }

    @Nullable
    private static Cell find(List<Cell> cells, String id) {
        for (Cell cell : cells) {
            if (cell.id().equals(id)) {
                return cell;
            }
        }
        return null;
    }

    private static boolean overBar(double mx, int barX) {
        return mx >= barX - 6 && mx < barX + CELL + 6;
    }

    /** Slot (>= 1, the editor keeps slot 0) the dragged button would take in the bar without itself. */
    private static int insertIndex(List<Cell> pinned, double my, int top) {
        int count = 0;
        for (Cell cell : pinned) {
            if (!cell.id().equals(dragId)) {
                count++;
            }
        }
        int index = (int) Math.floor((my - top + STEP / 2.0) / STEP);
        return Math.max(1, Math.min(count, index));
    }

    private static void drop(TerminalBarAccess bar, List<Cell> pinned, double mx, double my, int barX, int top) {
        List<String> ids = new ArrayList<>();
        for (Cell cell : pinned) {
            if (!cell.id().equals(dragId)) {
                ids.add(cell.id());
            }
        }
        if (overBar(mx, barX)) {
            ids.add(Math.max(1, Math.min(ids.size(), insertIndex(pinned, my, top))), dragId);
        } else if (dragFromBar && mx < barX - 6) {
            // Dropped on the tray side: unpinned (already removed from the list).
        } else {
            return;
        }
        SccConfig.get().terminalBar = ids;
        SccConfig.save();
        bar.sccadd$rebuild().run();
    }

    private static void drawCell(GuiGraphics g, Cell cell, boolean hover, boolean locked) {
        if (hover) {
            g.fill(cell.x() - 1, cell.y() - 1, cell.x() + CELL + 1, cell.y() + CELL + 1, SccGui.ROW_HOVER);
        }
        SccGui.glyph(g, cell.glyph(), cell.x(), cell.y(), CELL, CELL, locked || hover ? SccGui.ACCENT : SccGui.TEXT);
    }
}
