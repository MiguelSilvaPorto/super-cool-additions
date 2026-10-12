package com.sccadd.client;

import com.sccadd.machine.MachineForcePayload;
import de.scc.calculator.calc.AnalysisManager;
import de.scc.calculator.calc.RecipeBook;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Pick which machine a Processing Calculator acts as. The list is every machine SCC knows a recipe
 * for; the top row goes back to detecting whatever stands in front. Typing a block id such as
 * {@code minecraft:furnace} also works for machines that are not in the list.
 */
public final class MachinePickerScreen extends Screen {
    private static final int W = 260;
    private static final int ROW = 20;
    private static final int ROWS = 9;

    private final Screen parent;
    private final BlockPos pos;
    private final String current;
    private final List<String> all = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private EditBox search;
    private int scroll;

    public MachinePickerScreen(Screen parent, BlockPos pos, String current) {
        super(Component.literal("Machine"));
        this.parent = parent;
        this.pos = pos;
        this.current = current == null ? "" : current;
        this.all.addAll(knownMachines());
    }

    @SuppressWarnings("unchecked")
    private static List<String> knownMachines() {
        List<String> out = new ArrayList<>();
        try {
            RecipeBook book = AnalysisManager.get().book();
            Field field = RecipeBook.class.getDeclaredField("machineCategories");
            field.setAccessible(true);
            Map<String, Set<String>> map = (Map<String, Set<String>>) field.get(book);
            out.addAll(map.keySet());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // the list stays empty; typing an id still works
        }
        out.sort(String::compareTo);
        return out;
    }

    private static ItemStack iconOf(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static String nameOf(String id) {
        ItemStack stack = iconOf(id);
        return stack.isEmpty() ? id : stack.getHoverName().getString();
    }

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return (this.height - (60 + ROWS * ROW)) / 2;
    }

    @Override
    protected void init() {
        this.search = new EditBox(this.font, this.left() + 8, this.top() + 24, W - 16, 16, Component.literal("Search"));
        this.search.setResponder(text -> {
            this.scroll = 0;
            this.refilter();
        });
        this.addRenderableWidget(this.search);
        this.setInitialFocus(this.search);
        this.refilter();
    }

    private void refilter() {
        String query = this.search == null ? "" : this.search.getValue().trim().toLowerCase(Locale.ROOT);
        this.shown.clear();
        this.shown.add("");
        for (String id : this.all) {
            if (query.isEmpty() || id.toLowerCase(Locale.ROOT).contains(query)
                    || nameOf(id).toLowerCase(Locale.ROOT).contains(query)) {
                this.shown.add(id);
            }
        }
        if (query.contains(":") && ResourceLocation.tryParse(query) != null && !this.shown.contains(query)) {
            this.shown.add(query);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    private void choose(String id) {
        PacketDistributor.sendToServer(new MachineForcePayload(this.pos, id));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0xB0000000);
        int x = this.left();
        int y = this.top();
        int h = 60 + ROWS * ROW;
        g.fill(x - 1, y - 1, x + W + 1, y + h + 1, 0xFF4FD1BE);
        g.fill(x, y, x + W, y + h, 0xFF1A2224);
        g.drawString(this.font, "Act as machine", x + 8, y + 8, 0xFF4FD1BE, false);
        String now = this.current.isEmpty() ? "now: detected from the block in front" : "now: " + this.current;
        g.drawString(this.font, this.font.plainSubstrByWidth(now, W - 130), x + 100, y + 8, 0xFF94A3A9, false);
        super.render(g, mx, my, pt);
        Font font = this.font;
        for (int i = 0; i < ROWS; i++) {
            int index = this.scroll + i;
            if (index >= this.shown.size()) {
                break;
            }
            String id = this.shown.get(index);
            int ry = y + 48 + i * ROW;
            boolean hover = mx >= x + 4 && mx < x + W - 4 && my >= ry && my < ry + ROW;
            boolean active = id.equals(this.current);
            g.fill(x + 4, ry, x + W - 4, ry + ROW - 2, hover ? 0xFF2A3F44 : (active ? 0xFF22302F : 0xFF141A20));
            if (id.isEmpty()) {
                g.drawString(font, "Auto - use the block in front", x + 10, ry + 6, 0xFFFFB454, false);
            } else {
                ItemStack icon = iconOf(id);
                if (!icon.isEmpty()) {
                    g.renderItem(icon, x + 6, ry + 1);
                }
                g.drawString(font, font.plainSubstrByWidth(nameOf(id), 130), x + 26, ry + 6, 0xFFE6ECEE, false);
                g.drawString(font, font.plainSubstrByWidth(id, 96), x + W - 104, ry + 6, 0xFF94A3A9, false);
            }
        }
        g.drawString(font, "Outside automation: put a chest in front, pick the machine it stands for, and let pipes do the work.",
                x + 8, y + h - 10, 0xFF94A3A9, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = this.left();
        int y = this.top();
        for (int i = 0; i < ROWS; i++) {
            int index = this.scroll + i;
            int ry = y + 48 + i * ROW;
            if (index < this.shown.size() && mx >= x + 4 && mx < x + W - 4 && my >= ry && my < ry + ROW) {
                this.choose(this.shown.get(index));
                return true;
            }
        }
        if (mx < x || mx > x + W || my < y || my > y + 60 + ROWS * ROW) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        this.scroll = Math.max(0, Math.min(Math.max(0, this.shown.size() - ROWS), this.scroll - (int) Math.signum(sy)));
        return true;
    }
}
