package com.sccadd.client.chain;

import com.sccadd.chain.ChainData;
import com.sccadd.chain.ChainHolder;
import com.sccadd.chain.ChainPayload;
import com.sccadd.requester.BigMath;
import de.scc.calculator.calc.SccRecipe;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.client.screen.ItemPickerScreen;
import de.scc.calculator.client.screen.SccGui;
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
 * one recipe you pick, joined by arrows that give the order they run in. The arrows may loop back,
 * which is the point: a loop of recipes that feed each other is described by you instead of being
 * solved by the planner.
 */
public final class ChainEditorScreen extends Screen {
    private static final int BOX_W = 138;
    private static final int BOX_H = 68;
    private static final int TOP_BAR = 26;
    private static final int PORT = 7;

    private final Screen parent;
    private final BlockPos pos;
    private final int row;
    private final ChainData chain;

    private int panX = 24;
    private int panY = 20;

    @Nullable
    private ChainData.Node dragging;
    private int dragOffX;
    private int dragOffY;
    @Nullable
    private ChainData.Node linkFrom;
    private boolean panning;
    private boolean moved;

    /** 0 = none, 1 = target, 2 = batch. */
    private int field;
    @Nullable
    private ChainData.Node fieldNode;
    private String fieldText = "";

    @Nullable
    private ChainData.Node recipeFor;
    private List<SccRecipe> recipes = List.of();
    private int recipeScroll;

    public ChainEditorScreen(Screen parent, BlockPos pos, int row, @Nullable ChainData existing) {
        super(Component.literal("Production chain"));
        this.parent = parent;
        this.pos = pos;
        this.row = row;
        this.chain = existing == null ? new ChainData() : ChainData.load(existing.save());
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
        return this.boxX(node) + 6;
    }

    private int slotY(ChainData.Node node) {
        return this.boxY(node) + 18;
    }

    private int targetX(ChainData.Node node) {
        return this.boxX(node) + 28;
    }

    private int batchX(ChainData.Node node) {
        return this.boxX(node) + 84;
    }

    private int recipeY(ChainData.Node node) {
        return this.boxY(node) + 40;
    }

    private int outX(ChainData.Node node) {
        return this.boxX(node) + BOX_W;
    }

    private int inX(ChainData.Node node) {
        return this.boxX(node);
    }

    private int midY(ChainData.Node node) {
        return this.boxY(node) + BOX_H / 2;
    }

    @Nullable
    private ChainData.Node nodeAt(double mx, double my) {
        for (int i = this.chain.nodes.size() - 1; i >= 0; i--) {
            ChainData.Node node = this.chain.nodes.get(i);
            if (inside(mx, my, this.boxX(node), this.boxY(node), BOX_W, BOX_H)) {
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

    // ---- editing ----

    private void addBox() {
        ChainData.Node node = new ChainData.Node();
        int next = 1;
        for (ChainData.Node other : this.chain.nodes) {
            next = Math.max(next, other.id + 1);
        }
        node.id = next;
        int count = this.chain.nodes.size();
        node.x = 8 + (count % 4) * (BOX_W + 26) - this.panX + 24;
        node.y = 8 + (count / 4) * (BOX_H + 26);
        if (this.chain.nodes.size() < ChainData.MAX_NODES) {
            this.chain.nodes.add(node);
            this.send();
        }
    }

    private void deleteBox(ChainData.Node node) {
        this.chain.nodes.remove(node);
        this.chain.links.removeIf(link -> link[0] == node.id || link[1] == node.id);
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
        this.recipes = ChainSteps.recipesFor(key);
        this.recipeFor = this.recipes.isEmpty() ? null : node;
        this.recipeScroll = 0;
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

    // ---- input ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.recipeFor != null) {
            return this.clickRecipes(mx, my, button);
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
            if (button == 0) {
                this.panning = true;
            }
            return true;
        }
        // delete
        if (button == 0 && inside(mx, my, this.boxX(node) + BOX_W - 13, this.boxY(node) + 2, 11, 11)) {
            this.deleteBox(node);
            return true;
        }
        // output port: drag to link, right-click to cut
        if (inside(mx, my, this.outX(node) - PORT, this.midY(node) - PORT, PORT * 2, PORT * 2)) {
            if (button == 1) {
                this.chain.links.removeIf(link -> link[0] == node.id);
                this.send();
            } else {
                this.linkFrom = node;
            }
            return true;
        }
        if (button == 0 && inside(mx, my, this.slotX(node), this.slotY(node), 18, 18)) {
            this.commitField();
            Minecraft.getInstance().setScreen(new ItemPickerScreen(this, key -> this.setItem(node, key)));
            return true;
        }
        if (button == 0 && inside(mx, my, this.targetX(node), this.slotY(node), 52, 18)) {
            this.beginField(node, 1);
            return true;
        }
        if (button == 0 && inside(mx, my, this.batchX(node), this.slotY(node), 46, 18)) {
            this.beginField(node, 2);
            return true;
        }
        if (button == 0 && inside(mx, my, this.slotX(node), this.recipeY(node), BOX_W - 12, 22)) {
            this.commitField();
            if (node.item.isEmpty()) {
                return true;
            }
            this.openRecipes(node);
            return true;
        }
        this.commitField();
        if (button == 0) {
            this.dragging = node;
            this.dragOffX = (int) mx - this.boxX(node);
            this.dragOffY = (int) my - this.boxY(node);
            this.moved = false;
            this.chain.nodes.remove(node);
            this.chain.nodes.add(node);
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
        if (this.recipeFor != null) {
            this.recipeScroll = Math.max(0, Math.min(Math.max(0, this.recipes.size() - 8), this.recipeScroll - (int) Math.signum(sy)));
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
        if (key == 256 && this.recipeFor != null) {
            this.recipeFor = null;
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // ---- recipe list ----

    private int listX() {
        return this.width / 2 - 170;
    }

    private int listY() {
        return 40;
    }

    private boolean clickRecipes(double mx, double my, int button) {
        if (!inside(mx, my, this.listX(), this.listY(), 340, 12 + 8 * 24 + 8)) {
            this.recipeFor = null;
            return true;
        }
        for (int i = 0; i < 8; i++) {
            int index = this.recipeScroll + i;
            if (index >= this.recipes.size()) {
                break;
            }
            int y = this.listY() + 20 + i * 24;
            if (inside(mx, my, this.listX() + 4, y, 332, 22)) {
                SccRecipe recipe = this.recipes.get(index);
                ChainSteps.apply(this.recipeFor, recipe);
                this.recipeFor = null;
                this.send();
                return true;
            }
        }
        return true;
    }

    // ---- drawing ----

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int fill, int edge) {
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
    }

    private void arrow(GuiGraphics g, ChainData.Node from, ChainData.Node to, int color) {
        int x1 = this.outX(from);
        int y1 = this.midY(from);
        int x2 = this.inX(to);
        int y2 = this.midY(to);
        int a = x1 + 14;
        int b = x2 - 14;
        int ym = (y1 + y2) / 2;
        if (b < a) {
            ym = Math.min(this.boxY(from), this.boxY(to)) - 12;
        }
        this.line(g, x1, y1, a, y1, color);
        this.line(g, a, y1, a, ym, color);
        this.line(g, a, ym, b, ym, color);
        this.line(g, b, ym, b, y2, color);
        this.line(g, b, y2, x2, y2, color);
        this.line(g, x2 - 4, y2 - 3, x2 - 4, y2 + 3, color);
        this.line(g, x2 - 3, y2 - 2, x2 - 3, y2 + 2, color);
        this.line(g, x2 - 2, y2 - 1, x2 - 2, y2 + 1, color);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Font font = this.font;
        g.fill(0, 0, this.width, this.height, 0xE00B1116);
        g.fill(0, 0, this.width, TOP_BAR, SccGui.HEADER | 0xFF000000);
        g.drawString(font, "Production chain - row " + (this.row + 1), this.width / 2 - font.width("Production chain - row 9") / 2, 9, SccGui.ACCENT | 0xFF000000, false);
        frame(g, 8, 4, 70, 18, SccGui.PANEL | 0xFF000000, SccGui.BORDER | 0xFF000000);
        g.drawString(font, "+ Box", 22, 9, SccGui.TEXT | 0xFF000000, false);
        frame(g, this.width - 70, 4, 64, 18, SccGui.PANEL | 0xFF000000, SccGui.BORDER | 0xFF000000);
        g.drawString(font, "Done", this.width - 54, 9, SccGui.TEXT | 0xFF000000, false);
        String hint = "Drag boxes. Drag from the right dot to another box to link it; right-click the dot to cut. Boxes run in arrow order, round and round.";
        g.drawString(font, font.plainSubstrByWidth(hint, this.width - 160), 88, 9, SccGui.MUTED | 0xFF000000, false);

        g.enableScissor(0, TOP_BAR, this.width, this.height);
        List<ChainData.Node> flow = this.chain.flow();
        for (int[] link : this.chain.links) {
            ChainData.Node from = this.chain.byId(link[0]);
            ChainData.Node to = this.chain.byId(link[1]);
            if (from != null && to != null) {
                this.arrow(g, from, to, SccGui.ACCENT | 0xFF000000);
            }
        }
        if (this.linkFrom != null) {
            this.line(g, this.outX(this.linkFrom), this.midY(this.linkFrom), mx, my, 0xFFFFFFFF);
        }
        List<Runnable> late = new ArrayList<>();
        for (ChainData.Node node : this.chain.nodes) {
            this.drawBox(g, font, node, flow.indexOf(node) + 1, mx, my, late);
        }
        g.disableScissor();
        for (Runnable task : late) {
            task.run();
        }
        if (this.recipeFor != null) {
            this.drawRecipes(g, font, mx, my);
        }
        if (this.chain.nodes.isEmpty() && this.recipeFor == null) {
            g.drawCenteredString(font, "Press + Box to add the first step. The first box is where the chain starts.", this.width / 2, this.height / 2, SccGui.MUTED | 0xFF000000);
        }
    }

    private void drawBox(GuiGraphics g, Font font, ChainData.Node node, int order, int mx, int my, List<Runnable> late) {
        int x = this.boxX(node);
        int y = this.boxY(node);
        boolean hover = inside(mx, my, x, y, BOX_W, BOX_H) && this.recipeFor == null;
        frame(g, x, y, BOX_W, BOX_H, SccGui.PANEL | 0xFF000000, (hover ? SccGui.ACCENT : SccGui.BORDER) | 0xFF000000);
        g.fill(x + 1, y + 1, x + BOX_W - 1, y + 14, SccGui.HEADER | 0xFF000000);
        g.drawString(font, order == 1 ? "1  start" : Integer.toString(order), x + 5, y + 4, SccGui.TEXT | 0xFF000000, false);
        g.drawString(font, "x", x + BOX_W - 10, y + 4, SccGui.BAD | 0xFF000000, false);

        StackKey key = node.item.isEmpty() ? null : StackKey.decode(node.item);
        int sx = this.slotX(node);
        int sy = this.slotY(node);
        frame(g, sx, sy, 18, 18, 0xFF10161C, SccGui.BORDER | 0xFF000000);
        if (key != null) {
            ItemStack shown = key.display();
            g.renderItem(shown, sx + 1, sy + 1);
            if (inside(mx, my, sx, sy, 18, 18) && this.recipeFor == null) {
                late.add(() -> g.renderTooltip(font, shown, mx, my));
            }
        }
        this.drawField(g, font, node, 1, this.targetX(node), sy, 52, Long.toString(node.target).isEmpty() ? "" : BigMath.compact(node.target), "keep");
        this.drawField(g, font, node, 2, this.batchX(node), sy, 46, node.batch > 0 ? Integer.toString(node.batch) : "all", "per run");

        int ry = this.recipeY(node);
        frame(g, sx, ry, BOX_W - 12, 22, 0xFF10161C, (node.step.isEmpty() ? SccGui.WARN : SccGui.BORDER) | 0xFF000000);
        CraftStep step = ChainSteps.stepOf(node);
        if (step == null) {
            g.drawString(font, key == null ? "pick an item first" : "click to pick a recipe", sx + 4, ry + 7, SccGui.MUTED | 0xFF000000, false);
        } else {
            int ix = sx + 3;
            int shownInputs = 0;
            for (CraftStep.Ing input : step.inputs()) {
                if (shownInputs >= 4) {
                    break;
                }
                g.pose().pushPose();
                g.pose().translate(ix, ry + 3, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.renderItem(input.item().display(), 0, 0);
                g.pose().popPose();
                g.drawString(font, Integer.toString(input.count()), ix + 8, ry + 12, 0xFFFFFFFF, true);
                ix += 17;
                shownInputs++;
            }
            g.drawString(font, "→", ix + 1, ry + 7, SccGui.MUTED | 0xFF000000, false);
            g.renderItem(step.output().display(), ix + 11, ry + 3);
            g.drawString(font, Integer.toString(step.outputCount()), ix + 21, ry + 12, 0xFFFFFFFF, true);
            String machine = step.machineId() == null ? "" : step.machineId();
            if (inside(mx, my, sx, ry, BOX_W - 12, 22) && this.recipeFor == null) {
                late.add(() -> g.renderComponentTooltip(font, List.of(
                        Component.literal(machine.isEmpty() ? "No machine" : machine),
                        Component.literal("Click to pick another recipe")), mx, my));
            }
        }
        // ports
        g.fill(this.inX(node) - 3, this.midY(node) - 3, this.inX(node) + 3, this.midY(node) + 4, SccGui.MUTED | 0xFF000000);
        g.fill(this.outX(node) - 3, this.midY(node) - 3, this.outX(node) + 4, this.midY(node) + 4,
                (this.linkOf(node) != null ? SccGui.ACCENT : SccGui.MUTED) | 0xFF000000);
    }

    private void drawField(GuiGraphics g, Font font, ChainData.Node node, int which, int x, int y, int w, String shown, String label) {
        boolean editing = this.field == which && this.fieldNode == node;
        frame(g, x, y, w, 18, 0xFF10161C, (editing ? SccGui.ACCENT : SccGui.BORDER) | 0xFF000000);
        String text = editing ? this.fieldText + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "") : shown;
        g.drawString(font, font.plainSubstrByWidth(text, w - 6), x + 3, y + 5, SccGui.TEXT | 0xFF000000, false);
        if (!editing) {
            g.pose().pushPose();
            g.pose().translate(x + 1, y - 7, 0);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(font, label, 0, 0, SccGui.MUTED | 0xFF000000, false);
            g.pose().popPose();
        }
    }

    private void drawRecipes(GuiGraphics g, Font font, int mx, int my) {
        int x = this.listX();
        int y = this.listY();
        g.fill(0, 0, this.width, this.height, 0x99000000);
        frame(g, x, y, 340, 12 + 8 * 24 + 8, SccGui.PANEL | 0xFF000000, SccGui.ACCENT | 0xFF000000);
        g.drawString(font, "Pick the recipe this box uses (" + this.recipes.size() + ")", x + 6, y + 6, SccGui.TEXT | 0xFF000000, false);
        List<Runnable> late = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            int index = this.recipeScroll + i;
            if (index >= this.recipes.size()) {
                break;
            }
            SccRecipe recipe = this.recipes.get(index);
            int ry = y + 20 + i * 24;
            boolean hover = inside(mx, my, x + 4, ry, 332, 22);
            g.fill(x + 4, ry, x + 336, ry + 22, hover ? SccGui.ROW_HOVER | 0xFF000000 : (i % 2 == 0 ? SccGui.ROW_ALT | 0xFF000000 : 0xFF141A20));
            int ix = x + 8;
            int shown = 0;
            for (SccRecipe.Input input : recipe.inputs()) {
                if (shown >= 6 || input.options().isEmpty()) {
                    break;
                }
                g.renderItem(input.options().get(0).display(), ix, ry + 3);
                g.drawString(font, Integer.toString(input.count()), ix + 9, ry + 12, 0xFFFFFFFF, true);
                ix += 20;
                shown++;
            }
            g.drawString(font, "→", ix + 2, ry + 7, SccGui.MUTED | 0xFF000000, false);
            g.renderItem(recipe.output().display(), ix + 14, ry + 3);
            g.drawString(font, Integer.toString(recipe.outputCount()), ix + 23, ry + 12, 0xFFFFFFFF, true);
            String type = recipe.typeName() == null ? "" : recipe.typeName();
            g.drawString(font, font.plainSubstrByWidth(type, 332 - (ix + 44 - x)), ix + 44, ry + 7, SccGui.MUTED | 0xFF000000, false);
        }
    }
}
