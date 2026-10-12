package com.sccadd.client.chain;

import com.sccadd.chain.ChainData;
import com.sccadd.chain.ChainHolder;
import com.sccadd.chain.ChainPayload;
import com.sccadd.requester.BigMath;
import de.scc.calculator.calc.SccRecipe;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.client.screen.ItemPickerScreen;
import de.scc.calculator.network.CraftStep;
import de.scc.calculator.network.RequesterBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The chain editor of one requester row: boxes you drag around, each keeping one item stocked with
 * one recipe, joined by arrows that give the order they run in. The arrows may loop back, which is
 * the point: a loop of recipes that feed each other is described by you instead of being solved by
 * the planner.
 *
 * <p>Reading the picture: every box has an IN plate on its left and an OUT plate on its right. A
 * link always leaves an OUT plate and enters an IN plate, shown with dots that run along it in the
 * direction the chain follows. The star box is where the chain starts, and its item is what the
 * requester row shows.</p>
 */
public final class ChainEditorScreen extends Screen {
    private static final int BOX_W = 158;
    private static final int BOX_H = 84;
    private static final int TOP_BAR = 26;
    private static final int PLATE_W = 14;
    private static final int PLATE_H = 22;

    private static final int TEXT = 0xFFE6ECEE;
    private static final int MUTED = 0xFF94A3A9;
    private static final int PANEL = 0xFF1A2224;
    private static final int PANEL_DARK = 0xFF10161C;
    private static final int HEAD = 0xFF22302F;
    private static final int EDGE = 0xFF3B4A4F;
    private static final int ACCENT = 0xFF4FD1BE;
    private static final int IN_COLOR = 0xFFFFB454;
    private static final int GOOD = 0xFF6FD26F;
    private static final int WARN = 0xFFE8B94A;
    private static final int BAD = 0xFFE86A6A;
    private static final int[] LINK_COLORS = {0xFF4FD1BE, 0xFFE0A3FF, 0xFFFFD166, 0xFF8EC5FF, 0xFFFF9B8E, 0xFFA8E68E};

    private final Screen parent;
    private final BlockPos pos;
    private final int row;
    private final ChainData chain;
    /** Drawing and hit order, last = on top. */
    private final List<ChainData.Node> order = new ArrayList<>();

    private int panX = 40;
    private int panY = 24;

    @Nullable
    private ChainData.Node dragging;
    private int dragOffX;
    private int dragOffY;
    private boolean moved;
    @Nullable
    private ChainData.Node linkFrom;
    private boolean panning;
    /** The link under the mouse: {from id, to id}. */
    @Nullable
    private int[] hoverLink;

    /** 0 = none, 1 = target, 2 = batch. */
    private int field;
    @Nullable
    private ChainData.Node fieldNode;
    private String fieldText = "";

    @Nullable
    private ChainData.Node recipeFor;
    @Nullable
    private RecipePickerHost picker;

    public ChainEditorScreen(Screen parent, BlockPos pos, int row, @Nullable ChainData existing) {
        super(Component.literal("Production chain"));
        this.parent = parent;
        this.pos = pos;
        this.row = row;
        this.chain = existing == null ? new ChainData() : ChainData.load(existing.save());
        this.order.addAll(this.chain.nodes);
    }

    public static void open(Screen parent, RequesterBlockEntity be, int row) {
        ChainData existing = ((ChainHolder) be).sccadd$chain(row);
        Minecraft.getInstance().setScreen(new ChainEditorScreen(parent, be.getBlockPos(), row, existing));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void send() {
        PacketDistributor.sendToServer(new ChainPayload(this.pos, this.row, this.chain.save()));
    }

    @Override
    public void removed() {
        this.commitField();
        this.send();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    /** The stock the server counted for a box, read from the live block entity. */
    private long haveOf(ChainData.Node node) {
        if (Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(this.pos) instanceof RequesterBlockEntity be) {
            ChainData live = ((ChainHolder) be).sccadd$chain(this.row);
            if (live != null) {
                ChainData.Node same = live.byId(node.id);
                if (same != null) {
                    return same.have;
                }
            }
        }
        return 0L;
    }

    // ---- geometry ----

    private int boxX(ChainData.Node node) {
        return this.panX + node.x;
    }

    private int boxY(ChainData.Node node) {
        return this.panY + TOP_BAR + node.y;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int slotX(ChainData.Node node) {
        return this.boxX(node) + 8;
    }

    private int rowOneY(ChainData.Node node) {
        return this.boxY(node) + 28;
    }

    private int targetX(ChainData.Node node) {
        return this.boxX(node) + 32;
    }

    private int batchX(ChainData.Node node) {
        return this.boxX(node) + 96;
    }

    private int recipeY(ChainData.Node node) {
        return this.boxY(node) + 54;
    }

    private int midY(ChainData.Node node) {
        return this.boxY(node) + BOX_H / 2;
    }

    /** The IN plate sits outside the left edge, the OUT plate outside the right edge. */
    private int inPlateX(ChainData.Node node) {
        return this.boxX(node) - PLATE_W;
    }

    private int outPlateX(ChainData.Node node) {
        return this.boxX(node) + BOX_W;
    }

    private int plateY(ChainData.Node node) {
        return this.midY(node) - PLATE_H / 2;
    }

    @Nullable
    private ChainData.Node nodeAt(double mx, double my) {
        for (int i = this.order.size() - 1; i >= 0; i--) {
            ChainData.Node node = this.order.get(i);
            if (inside(mx, my, this.boxX(node) - PLATE_W, this.boxY(node), BOX_W + 2 * PLATE_W, BOX_H)) {
                return node;
            }
        }
        return null;
    }

    private int[] linkOf(ChainData.Node node) {
        for (int[] link : this.chain.links) {
            if (link[0] == node.id) {
                return link;
            }
        }
        return null;
    }

    private int orderOf(ChainData.Node node) {
        return this.chain.flow().indexOf(node) + 1;
    }

    // ---- editing ----

    private void addBox() {
        ChainData.Node node = new ChainData.Node();
        int next = 1;
        for (ChainData.Node other : this.chain.nodes) {
            next = Math.max(next, other.id + 1);
        }
        node.id = next;
        int count = this.chain.nodes.size();
        node.x = (count % 3) * (BOX_W + 70) - 8;
        node.y = (count / 3) * (BOX_H + 60) + 8;
        this.chain.nodes.add(node);
        this.order.add(node);
        if (this.chain.nodes.size() == 1) {
            this.chain.startId = node.id;
        }
        this.send();
    }

    private void deleteBox(ChainData.Node node) {
        this.chain.nodes.remove(node);
        this.order.remove(node);
        this.chain.links.removeIf(link -> link[0] == node.id || link[1] == node.id);
        if (this.chain.startId == node.id) {
            this.chain.startId = this.chain.nodes.isEmpty() ? 0 : this.chain.nodes.get(0).id;
        }
        this.send();
    }

    private void setItem(ChainData.Node node, StackKey key) {
        node.item = key.encode();
        node.step = "";
        node.alts = new ArrayList<>();
        this.send();
        this.openRecipes(node);
    }

    private void openRecipes(ChainData.Node node) {
        StackKey key = node.item.isEmpty() ? null : StackKey.decode(node.item);
        if (key == null) {
            return;
        }
        CraftStep current = ChainSteps.stepOf(node);
        SccRecipe currentRecipe = null;
        if (current != null) {
            for (SccRecipe recipe : ChainSteps.recipesFor(key)) {
                if (recipe.id().equals(current.recipeId())) {
                    currentRecipe = recipe;
                }
            }
        }
        this.picker = RecipePickerHost.open(key, currentRecipe, this.width, this.height, recipe -> {
            ChainSteps.apply(node, recipe);
            this.send();
            this.closeRecipes();
        }, this::closeRecipes);
        this.recipeFor = this.picker == null ? null : node;
    }

    private void closeRecipes() {
        this.picker = null;
        this.recipeFor = null;
    }

    private void beginField(ChainData.Node node, int which) {
        this.commitField();
        this.field = which;
        this.fieldNode = node;
        this.fieldText = which == 1 ? Long.toString(node.target) : (node.batch > 0 ? Integer.toString(node.batch) : "");
    }

    private void commitField() {
        if (this.field == 0 || this.fieldNode == null) {
            this.field = 0;
            return;
        }
        long value = this.fieldText.isBlank() ? 0 : BigMath.eval(this.fieldText);
        if (value != BigMath.INVALID && value >= 0) {
            if (this.field == 1) {
                this.fieldNode.target = Math.min(value, BigMath.MAX);
            } else {
                this.fieldNode.batch = (int) Math.min(value, 1_000_000L);
            }
            this.send();
        }
        this.field = 0;
        this.fieldNode = null;
    }

    private void cutLink(int[] link) {
        this.chain.links.removeIf(other -> other[0] == link[0] && other[1] == link[1]);
        this.hoverLink = null;
        this.send();
    }

    private void bringToFront(ChainData.Node node) {
        this.order.remove(node);
        this.order.add(node);
    }

    // ---- link paths ----

    /** The corner points of the link {@code from -> to}; leaves the OUT plate, enters the IN plate. */
    private List<int[]> path(ChainData.Node from, ChainData.Node to, int lane) {
        int x1 = this.outPlateX(from) + PLATE_W;
        int y1 = this.midY(from);
        int x2 = this.inPlateX(to);
        int y2 = this.midY(to);
        List<int[]> points = new ArrayList<>();
        points.add(new int[] {x1, y1});
        if (x2 - x1 >= 28) {
            int xm = (x1 + x2) / 2 + (lane - 2) * 5;
            points.add(new int[] {xm, y1});
            points.add(new int[] {xm, y2});
        } else {
            int below = Math.max(this.boxY(from), this.boxY(to)) + BOX_H + 16 + lane * 6;
            points.add(new int[] {x1 + 10 + lane * 3, y1});
            points.add(new int[] {x1 + 10 + lane * 3, below});
            points.add(new int[] {x2 - 10 - lane * 3, below});
            points.add(new int[] {x2 - 10 - lane * 3, y2});
        }
        points.add(new int[] {x2, y2});
        return points;
    }

    private static double length(List<int[]> points) {
        double total = 0;
        for (int i = 0; i + 1 < points.size(); i++) {
            total += Math.abs(points.get(i + 1)[0] - points.get(i)[0]) + Math.abs(points.get(i + 1)[1] - points.get(i)[1]);
        }
        return total;
    }

    /** The point {@code distance} along the path, and the unit direction there. */
    private static int[] along(List<int[]> points, double distance) {
        double left = distance;
        for (int i = 0; i + 1 < points.size(); i++) {
            int[] a = points.get(i);
            int[] b = points.get(i + 1);
            double seg = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]);
            if (left <= seg || i + 2 == points.size()) {
                double t = seg == 0 ? 0 : Math.min(1, left / seg);
                return new int[] {(int) (a[0] + (b[0] - a[0]) * t), (int) (a[1] + (b[1] - a[1]) * t),
                        Integer.signum(b[0] - a[0]), Integer.signum(b[1] - a[1])};
            }
            left -= seg;
        }
        int[] last = points.get(points.size() - 1);
        return new int[] {last[0], last[1], 1, 0};
    }

    private static boolean nearPath(List<int[]> points, double mx, double my, int tolerance) {
        for (int i = 0; i + 1 < points.size(); i++) {
            int[] a = points.get(i);
            int[] b = points.get(i + 1);
            if (mx >= Math.min(a[0], b[0]) - tolerance && mx <= Math.max(a[0], b[0]) + tolerance
                    && my >= Math.min(a[1], b[1]) - tolerance && my <= Math.max(a[1], b[1]) + tolerance) {
                return true;
            }
        }
        return false;
    }

    private int[] linkAt(double mx, double my) {
        for (int i = this.chain.links.size() - 1; i >= 0; i--) {
            int[] link = this.chain.links.get(i);
            ChainData.Node from = this.chain.byId(link[0]);
            ChainData.Node to = this.chain.byId(link[1]);
            if (from != null && to != null && nearPath(this.path(from, to, i % 5), mx, my, 4)) {
                return link;
            }
        }
        return null;
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.picker != null) {
            if (this.picker.contains(mx, my)) {
                this.picker.click(mx, my, this.font);
            } else {
                this.closeRecipes();
            }
            return true;
        }
        if (inside(mx, my, this.width - 70, 4, 64, 18)) {
            this.onClose();
            return true;
        }
        if (inside(mx, my, 8, 4, 70, 18)) {
            this.commitField();
            this.addBox();
            return true;
        }
        ChainData.Node node = this.nodeAt(mx, my);
        if (node == null) {
            this.commitField();
            int[] link = this.linkAt(mx, my);
            if (link != null) {
                int index = this.chain.links.indexOf(link);
                ChainData.Node from = this.chain.byId(link[0]);
                ChainData.Node to = this.chain.byId(link[1]);
                List<int[]> points = this.path(from, to, index % 5);
                int[] mid = along(points, length(points) / 2);
                if (button == 1 || inside(mx, my, mid[0] - 6, mid[1] - 6, 12, 12)) {
                    this.cutLink(link);
                    return true;
                }
            }
            if (button == 0) {
                this.panning = true;
            }
            return true;
        }
        this.bringToFront(node);
        int bx = this.boxX(node);
        int by = this.boxY(node);
        // OUT plate: drag to link, right-click to cut
        if (inside(mx, my, this.outPlateX(node), this.plateY(node), PLATE_W, PLATE_H)) {
            if (button == 1) {
                this.chain.links.removeIf(link -> link[0] == node.id);
                this.send();
            } else {
                this.linkFrom = node;
            }
            return true;
        }
        // IN plate: right-click cuts whatever arrives here
        if (inside(mx, my, this.inPlateX(node), this.plateY(node), PLATE_W, PLATE_H)) {
            if (button == 1) {
                this.chain.links.removeIf(link -> link[1] == node.id);
                this.send();
            }
            return true;
        }
        if (!inside(mx, my, bx, by, BOX_W, BOX_H)) {
            return true;
        }
        if (button == 0 && inside(mx, my, bx + BOX_W - 14, by + 3, 11, 11)) {
            this.deleteBox(node);
            return true;
        }
        if (button == 0 && inside(mx, my, bx + 4, by + 2, 56, 13)) {
            this.chain.startId = node.id;
            this.send();
            return true;
        }
        if (button == 0 && inside(mx, my, this.slotX(node), this.rowOneY(node), 18, 18)) {
            this.commitField();
            Minecraft.getInstance().setScreen(new ItemPickerScreen(this, key -> this.setItem(node, key)));
            return true;
        }
        if (button == 0 && inside(mx, my, this.targetX(node), this.rowOneY(node), 60, 18)) {
            this.beginField(node, 1);
            return true;
        }
        if (button == 0 && inside(mx, my, this.batchX(node), this.rowOneY(node), 54, 18)) {
            this.beginField(node, 2);
            return true;
        }
        if (button == 0 && inside(mx, my, this.slotX(node), this.recipeY(node), BOX_W - 16, 24)) {
            this.commitField();
            this.openRecipes(node);
            return true;
        }
        this.commitField();
        if (button == 0) {
            this.dragging = node;
            this.dragOffX = (int) mx - this.boxX(node);
            this.dragOffY = (int) my - this.boxY(node);
            this.moved = false;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.dragging != null) {
            this.dragging.x = (int) mx - this.dragOffX - this.panX;
            this.dragging.y = (int) my - this.dragOffY - this.panY - TOP_BAR;
            this.moved = true;
            return true;
        }
        if (this.panning) {
            this.panX += (int) dx;
            this.panY += (int) dy;
            return true;
        }
        return this.linkFrom != null || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (this.linkFrom != null) {
            ChainData.Node target = this.nodeAt(mx, my);
            if (target != null && target != this.linkFrom) {
                final ChainData.Node from = this.linkFrom;
                this.chain.links.removeIf(link -> link[0] == from.id);
                this.chain.links.add(new int[] {from.id, target.id});
                this.send();
            }
            this.linkFrom = null;
        }
        if (this.dragging != null && this.moved) {
            this.send();
        }
        this.dragging = null;
        this.panning = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (this.picker != null) {
            this.picker.scroll(sy);
            return true;
        }
        this.panY += (int) (sy * 14);
        return true;
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (this.field != 0 && BigMath.allowedChar(ch) && this.fieldText.length() < 24) {
            this.fieldText += ch;
            return true;
        }
        return super.charTyped(ch, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (this.field != 0) {
            if (key == 257 || key == 335) {
                this.commitField();
                return true;
            }
            if (key == 259) {
                if (!this.fieldText.isEmpty()) {
                    this.fieldText = this.fieldText.substring(0, this.fieldText.length() - 1);
                }
                return true;
            }
            if (key == 256) {
                this.field = 0;
                this.fieldNode = null;
                return true;
            }
        }
        if (key == 256 && this.picker != null) {
            this.closeRecipes();
            return true;
        }
        if ((key == 261 || key == 259) && this.hoverLink != null && this.field == 0) {
            this.cutLink(this.hoverLink);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // ---- drawing ----

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int fill, int edge) {
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private static void thick(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(Math.min(x1, x2) - 1, Math.min(y1, y2) - 1, Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
    }

    private void drawLink(GuiGraphics g, List<int[]> points, int color, boolean strong, long time) {
        for (int i = 0; i + 1 < points.size(); i++) {
            int[] a = points.get(i);
            int[] b = points.get(i + 1);
            thick(g, a[0], a[1], b[0], b[1], strong ? 0xFFFFFFFF : (color & 0x00FFFFFF) | 0xAA000000);
            if (strong) {
                continue;
            }
        }
        double total = length(points);
        // dots that run in the direction the chain follows
        double spacing = 26;
        double offset = (time / 28.0) % spacing;
        for (double d = offset; d < total; d += spacing) {
            int[] at = along(points, d);
            g.fill(at[0] - 2, at[1] - 2, at[0] + 2, at[1] + 2, 0xFFFFFFFF);
        }
        // the arrow head where the link enters
        int[] last = points.get(points.size() - 1);
        for (int k = 0; k < 5; k++) {
            g.fill(last[0] - 6 + k, last[1] - 5 + k, last[0] - 5 + k, last[1] + 6 - k, strong ? 0xFFFFFFFF : color);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font font = this.font;
        long time = System.currentTimeMillis();
        g.fill(0, 0, this.width, this.height, 0xE00B1116);
        g.fill(0, 0, this.width, TOP_BAR, HEAD);
        g.drawString(font, "Production chain - row " + (this.row + 1), this.width / 2 - font.width("Production chain - row 9") / 2, 9, ACCENT, false);
        frame(g, 8, 4, 70, 18, PANEL, EDGE);
        g.drawString(font, "+ Box", 28, 9, TEXT, false);
        frame(g, this.width - 70, 4, 64, 18, PANEL, EDGE);
        g.drawString(font, "Done", this.width - 52, 9, TEXT, false);

        boolean modal = this.picker != null;
        this.hoverLink = modal || this.nodeAt(mx, my) != null ? null : this.linkAt(mx, my);

        g.enableScissor(0, TOP_BAR, this.width, this.height - 16);
        for (int i = 0; i < this.chain.links.size(); i++) {
            int[] link = this.chain.links.get(i);
            ChainData.Node from = this.chain.byId(link[0]);
            ChainData.Node to = this.chain.byId(link[1]);
            if (from == null || to == null) {
                continue;
            }
            boolean hot = this.hoverLink != null && this.hoverLink[0] == link[0] && this.hoverLink[1] == link[1];
            List<int[]> points = this.path(from, to, i % 5);
            this.drawLink(g, points, LINK_COLORS[i % LINK_COLORS.length], hot, time);
            if (hot) {
                int[] mid = along(points, length(points) / 2);
                g.fill(mid[0] - 6, mid[1] - 6, mid[0] + 6, mid[1] + 6, BAD);
                g.drawString(font, "x", mid[0] - 2, mid[1] - 4, 0xFFFFFFFF, false);
            }
        }
        if (this.linkFrom != null) {
            int x1 = this.outPlateX(this.linkFrom) + PLATE_W;
            int y1 = this.midY(this.linkFrom);
            thick(g, x1, y1, mx, y1, 0xFFFFFFFF);
            thick(g, mx, y1, mx, my, 0xFFFFFFFF);
        }
        List<Runnable> late = new ArrayList<>();
        for (ChainData.Node node : this.order) {
            this.drawBox(g, font, node, mx, my, late, modal, time);
        }
        g.disableScissor();
        for (Runnable task : late) {
            task.run();
        }

        String legend = "OUT → IN: drag from a box's right OUT plate onto another box.  Dots run in the direction of the chain.  "
                + "Right-click a plate or click a link's x to cut.  ★ = start";
        g.fill(0, this.height - 14, this.width, this.height, HEAD);
        g.drawString(font, font.plainSubstrByWidth(legend, this.width - 12), 6, this.height - 11, MUTED, false);
        if (this.chain.nodes.isEmpty() && !modal) {
            g.drawCenteredString(font, "Press + Box to add the first step.", this.width / 2, this.height / 2, MUTED);
        }
        if (this.picker != null) {
            g.fill(0, 0, this.width, this.height, 0x99000000);
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            this.picker.draw(g, font, mx, my);
            g.pose().popPose();
        }
    }

    private void drawBox(GuiGraphics g, Font font, ChainData.Node node, int mx, int my, List<Runnable> late, boolean modal, long time) {
        int x = this.boxX(node);
        int y = this.boxY(node);
        boolean hover = !modal && inside(mx, my, x, y, BOX_W, BOX_H);
        boolean start = this.chain.start() == node;
        boolean target = this.linkFrom != null && this.linkFrom != node && this.nodeAt(mx, my) == node;
        frame(g, x, y, BOX_W, BOX_H, PANEL, target ? 0xFFFFFFFF : (hover ? ACCENT : (start ? WARN : EDGE)));
        g.fill(x + 1, y + 1, x + BOX_W - 1, y + 16, HEAD);
        int order = this.orderOf(node);
        String star = start ? "★ start" : "☆ #" + order;
        g.drawString(font, star, x + 5, y + 4, start ? WARN : MUTED, false);
        if (!start && order > 0) {
            g.drawString(font, "step " + order, x + 56, y + 4, MUTED, false);
        }
        g.drawString(font, "x", x + BOX_W - 10, y + 4, BAD, false);
        if (!modal && inside(mx, my, x + 4, y + 2, 56, 13)) {
            late.add(() -> g.renderComponentTooltip(font, List.of(
                    Component.literal(start ? "The chain starts here" : "Click to make this the start of the chain"),
                    Component.literal("The start box's item shows on the requester button")), mx, my));
        }

        StackKey key = node.item.isEmpty() ? null : StackKey.decode(node.item);
        int sx = this.slotX(node);
        int sy = this.rowOneY(node);
        frame(g, sx, sy, 18, 18, PANEL_DARK, EDGE);
        if (key != null) {
            ItemStack shown = key.display();
            g.renderItem(shown, sx + 1, sy + 1);
            if (!modal && inside(mx, my, sx, sy, 18, 18)) {
                late.add(() -> g.renderTooltip(font, shown, mx, my));
            }
        } else {
            g.drawString(font, "?", sx + 6, sy + 5, MUTED, false);
        }
        this.drawField(g, font, node, 1, this.targetX(node), sy, 60, BigMath.compact(node.target), "keep in stock");
        this.drawField(g, font, node, 2, this.batchX(node), sy, 54, node.batch > 0 ? Integer.toString(node.batch) : "all", "crafts per run");

        // the stock, in the title row
        long have = this.haveOf(node);
        boolean enough = have >= node.target;
        String stock = "have " + BigMath.compact(have);
        g.drawString(font, stock, x + BOX_W - 20 - font.width(stock), y + 4, enough ? GOOD : WARN, false);

        int ry = this.recipeY(node);
        frame(g, sx, ry, BOX_W - 16, 24, PANEL_DARK, node.step.isEmpty() ? WARN : EDGE);
        CraftStep step = ChainSteps.stepOf(node);
        if (step == null) {
            g.drawString(font, key == null ? "pick an item first" : "click to pick a recipe", sx + 5, ry + 8, MUTED, false);
        } else {
            int ix = sx + 4;
            int shownInputs = 0;
            for (CraftStep.Ing input : step.inputs()) {
                if (shownInputs >= 4) {
                    break;
                }
                g.pose().pushPose();
                g.pose().translate(ix, ry + 4, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.renderItem(input.item().display(), 0, 0);
                g.pose().popPose();
                g.drawString(font, Integer.toString(input.count()), ix + 9, ry + 13, 0xFFFFFFFF, true);
                ix += 19;
                shownInputs++;
            }
            g.drawString(font, "→", ix + 1, ry + 8, MUTED, false);
            g.renderItem(step.output().display(), ix + 12, ry + 4);
            g.drawString(font, Integer.toString(step.outputCount()), ix + 22, ry + 13, 0xFFFFFFFF, true);
            String machine = step.machineId() == null ? "" : step.machineId();
            if (!modal && inside(mx, my, sx, ry, BOX_W - 16, 24)) {
                late.add(() -> g.renderComponentTooltip(font, List.of(
                        Component.literal(machine.isEmpty() ? "No machine" : machine),
                        Component.literal("Click to pick another recipe")), mx, my));
            }
        }
        this.drawPlate(g, font, node, true, mx, my, late, modal, time);
        this.drawPlate(g, font, node, false, mx, my, late, modal, time);
    }

    private void drawPlate(GuiGraphics g, Font font, ChainData.Node node, boolean in, int mx, int my, List<Runnable> late, boolean modal, long time) {
        int x = in ? this.inPlateX(node) : this.outPlateX(node);
        int y = this.plateY(node);
        boolean hover = !modal && inside(mx, my, x, y, PLATE_W, PLATE_H);
        boolean connected = in ? this.chain.links.stream().anyMatch(l -> l[1] == node.id) : this.linkOf(node) != null;
        boolean asking = in && this.linkFrom != null && this.linkFrom != node && (time / 300) % 2 == 0;
        int color = in ? IN_COLOR : ACCENT;
        frame(g, x, y, PLATE_W, PLATE_H, hover || asking ? color : PANEL_DARK, color);
        int mid = y + PLATE_H / 2;
        int ink = hover || asking ? 0xFF10161C : color;
        // an arrow head pointing the way links travel: always to the right
        g.fill(x + 3, mid - 1, x + 9, mid + 1, ink);
        for (int k = 0; k < 4; k++) {
            g.fill(x + 8 + k, mid - 4 + k, x + 9 + k, mid + 4 - k, ink);
        }
        g.pose().pushPose();
        g.pose().translate(x + (in ? 1 : 2), y + PLATE_H - 8, 0);
        g.pose().scale(0.5f, 0.5f, 1f);
        g.drawString(font, in ? "IN" : "OUT", 0, 0, connected ? ink : (color & 0x00FFFFFF) | 0xAA000000, false);
        g.pose().popPose();
        if (hover) {
            late.add(() -> g.renderComponentTooltip(font, List.of(
                    Component.literal(in ? "IN: links arrive here" : "OUT: drag from here onto another box"),
                    Component.literal("Right-click to cut")), mx, my));
        }
    }

    private void drawField(GuiGraphics g, Font font, ChainData.Node node, int which, int x, int y, int w, String shown, String label) {
        boolean editing = this.field == which && this.fieldNode == node;
        frame(g, x, y, w, 18, PANEL_DARK, editing ? ACCENT : EDGE);
        String text = editing ? this.fieldText + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "") : shown;
        g.drawString(font, font.plainSubstrByWidth(text, w - 6), x + 3, y + 5, TEXT, false);
        g.pose().pushPose();
        g.pose().translate(x + 1, y - 8, 0);
        g.pose().scale(0.6f, 0.6f, 1f);
        g.drawString(font, label, 0, 0, MUTED, false);
        g.pose().popPose();
    }
}
