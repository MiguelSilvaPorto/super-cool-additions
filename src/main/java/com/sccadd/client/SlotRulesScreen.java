package com.sccadd.client;

import com.sccadd.slots.SlotRule;
import com.sccadd.slots.SlotRules;
import com.sccadd.slots.SlotRulesPayload;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.client.WirelessClient;
import de.scc.calculator.client.screen.SccGui;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Slot rules of the pocket terminal. The left shows the armor and off-hand slots and the whole
 * inventory; clicking a slot opens a pop-up:
 * <ul>
 *   <li>export: pick the item and/or fluid the slot should always hold, how many, and whether the
 *       system should order more when it runs out (and how many);</li>
 *   <li>import: pick what the slot sends to the system - its items, the fluid inside its bucket or
 *       tank, or both (fluid first, then the empty container).</li>
 * </ul>
 */
public final class SlotRulesScreen extends Screen {
    public enum Mode {
        EXPORT, IMPORT
    }

    private static final int CELL = 18;
    private static final int RESULT_COLS = 9;
    private static final int RESULT_ROWS = 5;

    private record Choice(@Nullable Item item, @Nullable Fluid fluid, ItemStack icon, String name, String id) {
    }

    @Nullable
    private final Screen parent;
    private final Mode mode;
    private final List<SlotRule> rules = new ArrayList<>();

    // The slot being edited and its draft values.
    private int editing = -1;
    private String draftItem = "";
    private String draftFluid = "";
    private SlotRule.Kind draftKind = SlotRule.Kind.IMPORT_ITEM;
    private int draftWant = 64;
    private boolean draftCraft;
    private int draftCraftAmount = 64;
    private List<Choice> results = List.of();
    private int resultScroll;
    private EditBox search;
    private EditBox wantBox;
    private EditBox craftBox;

    public SlotRulesScreen(@Nullable Screen parent, Mode mode) {
        super(Component.literal(mode == Mode.EXPORT ? "Export: system -> inventory" : "Import: inventory -> system"));
        this.parent = parent;
        this.mode = mode;
        this.rules.addAll(SlotRules.of(WirelessClient.heldTerminal()));
    }

    // ---- layout ----

    private int gridLeft() {
        return this.width / 2 - (CELL * 9) / 2 + 12;
    }

    private int gridTop() {
        return 44;
    }

    private int armorLeft() {
        return this.gridLeft() - CELL - 8;
    }

    /** Screen position of a player inventory slot. */
    private int[] slotPos(int slot) {
        int gx = this.gridLeft();
        int gy = this.gridTop();
        if (slot >= 9 && slot <= 35) {
            return new int[]{gx + (slot - 9) % 9 * CELL, gy + (slot - 9) / 9 * CELL};
        }
        if (slot >= 0 && slot <= 8) {
            return new int[]{gx + slot * CELL, gy + 3 * CELL + 6};
        }
        if (slot >= 36 && slot <= 39) {
            // head on top, feet below
            return new int[]{this.armorLeft(), gy + (39 - slot) * CELL};
        }
        return new int[]{this.armorLeft(), gy + 4 * CELL + 6};
    }

    private int slotAt(double mx, double my) {
        for (int slot = 0; slot < SlotRule.SLOT_COUNT; slot++) {
            int[] pos = this.slotPos(slot);
            if (mx >= pos[0] && mx < pos[0] + CELL && my >= pos[1] && my < pos[1] + CELL) {
                return slot;
            }
        }
        return -1;
    }

    @Nullable
    private SlotRule ruleOf(int slot) {
        for (SlotRule rule : this.rules) {
            if (rule.slot() == slot) {
                return rule;
            }
        }
        return null;
    }

    private boolean mine(SlotRule rule) {
        return rule.kind().isImport() == (this.mode == Mode.IMPORT);
    }

    // ---- popup geometry ----

    private int popupW() {
        return 214;
    }

    private int popupH() {
        return this.mode == Mode.EXPORT ? 238 : 96;
    }

    private int popupX() {
        return this.width / 2 - this.popupW() / 2;
    }

    private int popupY() {
        return Math.max(8, this.height / 2 - this.popupH() / 2);
    }

    private int resultsX() {
        return this.popupX() + 8;
    }

    private int resultsY() {
        return this.popupY() + 74;
    }

    // ---- saving ----

    private void push() {
        PacketDistributor.sendToServer(new SlotRulesPayload(SlotRules.encode(this.rules)));
    }

    private void open(int slot) {
        SlotRule existing = this.ruleOf(slot);
        if (existing != null && !this.mine(existing)) {
            return; // the slot already has a rule of the other direction
        }
        this.editing = slot;
        this.draftItem = existing == null ? "" : existing.item();
        this.draftFluid = existing == null ? "" : existing.fluid();
        this.draftKind = existing == null || !existing.kind().isImport() ? SlotRule.Kind.IMPORT_ITEM : existing.kind();
        this.draftWant = existing == null ? 64 : existing.want();
        this.draftCraft = existing != null && existing.craft();
        this.draftCraftAmount = existing == null ? 64 : existing.craftAmount();
        this.resultScroll = 0;
        this.refreshResults("");
        this.rebuildWidgets();
    }

    private void closePopup() {
        this.editing = -1;
        this.rebuildWidgets();
    }

    private void commit(boolean clear) {
        int slot = this.editing;
        this.rules.removeIf(rule -> rule.slot() == slot);
        if (!clear) {
            if (this.mode == Mode.EXPORT) {
                if (!this.draftItem.isBlank() || !this.draftFluid.isBlank()) {
                    this.rules.add(new SlotRule(slot, SlotRule.Kind.EXPORT, this.draftItem, this.draftFluid,
                            Math.max(1, this.draftWant), this.draftCraft, Math.max(1, this.draftCraftAmount)));
                }
            } else {
                this.rules.add(new SlotRule(slot, this.draftKind, "", "", 1, false, 1));
            }
        }
        this.push();
        this.closePopup();
    }

    // ---- results (items and fluids) ----

    private void refreshResults(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Choice> out = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (q.isEmpty()) {
            // Nothing typed: what is in the inventory, then every fluid.
            if (minecraft.player != null) {
                Inventory inventory = minecraft.player.getInventory();
                List<Item> seen = new ArrayList<>();
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack stack = inventory.getItem(i);
                    if (!stack.isEmpty() && !seen.contains(stack.getItem())) {
                        seen.add(stack.getItem());
                        out.add(itemChoice(stack.getItem()));
                    }
                }
            }
            for (Fluid fluid : BuiltInRegistries.FLUID) {
                addFluid(out, fluid, q);
            }
        } else {
            for (Fluid fluid : BuiltInRegistries.FLUID) {
                addFluid(out, fluid, q);
            }
            for (Item item : BuiltInRegistries.ITEM) {
                if (out.size() > 600) {
                    break;
                }
                if (item == Items.AIR) {
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                String name = item.getDescription().getString().toLowerCase(Locale.ROOT);
                if (name.contains(q) || id.toString().contains(q)) {
                    out.add(itemChoice(item));
                }
            }
        }
        this.results = out;
    }

    private static Choice itemChoice(Item item) {
        ItemStack icon = new ItemStack(item);
        return new Choice(item, null, icon, icon.getHoverName().getString(), BuiltInRegistries.ITEM.getKey(item).toString());
    }

    private static void addFluid(List<Choice> out, Fluid fluid, String q) {
        if (fluid == Fluids.EMPTY || !fluid.isSource(fluid.defaultFluidState())) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        String name = Component.translatable(fluid.getFluidType().getDescriptionId()).getString();
        if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q) && !id.toString().contains(q)) {
            return;
        }
        Item bucket = fluid.getBucket();
        ItemStack icon = new ItemStack(bucket == Items.AIR ? Items.BUCKET : bucket);
        out.add(new Choice(null, fluid, icon, name + " (fluid)", id.toString()));
    }

    // ---- widgets ----

    @Override
    protected void init() {
        this.clearWidgets();
        this.search = null;
        this.wantBox = null;
        this.craftBox = null;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 26, 100, 20).build());
        if (this.editing < 0) {
            return;
        }
        int px = this.popupX();
        int py = this.popupY();
        if (this.mode == Mode.IMPORT) {
            String[] labels = {"Items", "Fluid", "Both"};
            SlotRule.Kind[] kinds = {SlotRule.Kind.IMPORT_ITEM, SlotRule.Kind.IMPORT_FLUID, SlotRule.Kind.IMPORT_BOTH};
            for (int i = 0; i < 3; i++) {
                final SlotRule.Kind kind = kinds[i];
                Button button = Button.builder(Component.literal((this.draftKind == kind ? "> " : "") + labels[i]), b -> {
                    this.draftKind = kind;
                    this.rebuildWidgets();
                }).bounds(px + 8 + i * 66, py + 30, 64, 20).build();
                this.addRenderableWidget(button);
            }
            this.addRenderableWidget(Button.builder(Component.literal("Set"), b -> this.commit(false))
                    .bounds(px + 8, py + 68, 60, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> this.commit(true))
                    .bounds(px + 72, py + 68, 60, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.closePopup())
                    .bounds(px + 136, py + 68, 70, 20).build());
            return;
        }
        this.search = new EditBox(this.font, px + 8, py + 52, this.popupW() - 16, 16, Component.literal("Search"));
        this.search.setHint(Component.literal("Search items and fluids..."));
        this.search.setResponder(text -> {
            this.resultScroll = 0;
            this.refreshResults(text);
        });
        this.addRenderableWidget(this.search);
        int below = this.resultsY() + RESULT_ROWS * CELL + 6;
        this.wantBox = new EditBox(this.font, px + 60, below, 44, 16, Component.literal("Amount"));
        this.wantBox.setValue(Integer.toString(this.draftWant));
        this.wantBox.setResponder(text -> this.draftWant = parse(text, this.draftWant));
        this.addRenderableWidget(this.wantBox);
        this.addRenderableWidget(Button.builder(Component.literal("Craft more when empty: " + (this.draftCraft ? "On" : "Off")), b -> {
            this.draftCraft = !this.draftCraft;
            this.rebuildWidgets();
        }).bounds(px + 8, below + 20, 150, 20).build());
        this.craftBox = new EditBox(this.font, px + 162, below + 22, 44, 16, Component.literal("Craft amount"));
        this.craftBox.setValue(Integer.toString(this.draftCraftAmount));
        this.craftBox.setResponder(text -> this.draftCraftAmount = parse(text, this.draftCraftAmount));
        this.craftBox.active = this.draftCraft;
        this.addRenderableWidget(this.craftBox);
        this.addRenderableWidget(Button.builder(Component.literal("Set"), b -> this.commit(false))
                .bounds(px + 8, below + 46, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> this.commit(true))
                .bounds(px + 72, below + 46, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.closePopup())
                .bounds(px + 136, below + 46, 70, 20).build());
    }

    private static int parse(String text, int fallback) {
        try {
            return Math.max(1, Math.min(9999, Integer.parseInt(text.trim())));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.editing >= 0) {
            if (super.mouseClicked(mx, my, button)) {
                return true;
            }
            if (this.mode == Mode.EXPORT && button == 0) {
                int rx = this.resultsX();
                int ry = this.resultsY();
                if (mx >= rx && mx < rx + RESULT_COLS * CELL && my >= ry && my < ry + RESULT_ROWS * CELL) {
                    int index = (this.resultScroll + (int) ((my - ry) / CELL)) * RESULT_COLS + (int) ((mx - rx) / CELL);
                    if (index >= 0 && index < this.results.size()) {
                        Choice choice = this.results.get(index);
                        if (choice.fluid() != null) {
                            this.draftFluid = choice.id();
                        } else if (choice.item() != null) {
                            StackKey key = StackKey.of(new ItemStack(choice.item()));
                            this.draftItem = key == null ? "" : key.encode();
                        }
                    }
                    return true;
                }
                // The chosen item / fluid chips: click to clear.
                if (my >= this.popupY() + 22 && my < this.popupY() + 44) {
                    if (mx >= this.popupX() + 8 && mx < this.popupX() + 8 + 100) {
                        this.draftItem = "";
                    } else if (mx >= this.popupX() + 108 && mx < this.popupX() + 208) {
                        this.draftFluid = "";
                    }
                    return true;
                }
            }
            return true;
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        int slot = this.slotAt(mx, my);
        if (slot >= 0 && button == 0) {
            this.open(slot);
            return true;
        }
        if (slot >= 0 && button == 1) {
            SlotRule existing = this.ruleOf(slot);
            if (existing != null && this.mine(existing)) {
                this.rules.remove(existing);
                this.push();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (this.editing >= 0 && this.mode == Mode.EXPORT) {
            int rows = (this.results.size() + RESULT_COLS - 1) / RESULT_COLS;
            int max = Math.max(0, rows - RESULT_ROWS);
            this.resultScroll = Math.max(0, Math.min(max, this.resultScroll - (int) Math.signum(dy)));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256 && this.editing >= 0) { // escape closes the pop-up first
            this.closePopup();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(this.armorLeft() - 6, 30, this.gridLeft() + CELL * 9 + 6, this.gridTop() + 4 * CELL + 6 + CELL + 8, SccGui.PANEL);
        g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.literal(this.mode == Mode.EXPORT
                        ? "Click a slot to choose what it always holds. Right-click clears."
                        : "Click a slot to choose what it sends to the system. Right-click clears."),
                this.width / 2, 24, 0xAAAAAA);
        Minecraft minecraft = Minecraft.getInstance();
        List<Component> tip = null;
        for (int slot = 0; slot < SlotRule.SLOT_COUNT; slot++) {
            int[] pos = this.slotPos(slot);
            boolean over = mouseX >= pos[0] && mouseX < pos[0] + CELL && mouseY >= pos[1] && mouseY < pos[1] + CELL;
            g.fill(pos[0], pos[1], pos[0] + CELL - 1, pos[1] + CELL - 1, over && this.editing < 0 ? SccGui.ROW_HOVER : SccGui.PLATE_BG);
            SlotRule rule = this.ruleOf(slot);
            ItemStack stack = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(slot);
            if (stack.isEmpty() && rule != null && this.mine(rule) && rule.hasItem()) {
                StackKey key = StackKey.decode(rule.item());
                if (key != null) {
                    g.renderItem(key.display(), pos[0] + 1, pos[1] + 1);
                    g.fill(pos[0] + 1, pos[1] + 1, pos[0] + CELL - 2, pos[1] + CELL - 2, (0x99 << 24) | (SccGui.PANEL & 0xFFFFFF));
                }
            } else if (!stack.isEmpty()) {
                g.renderItem(stack, pos[0] + 1, pos[1] + 1);
                g.renderItemDecorations(this.font, stack, pos[0] + 1, pos[1] + 1);
            }
            if (rule != null) {
                int color = this.mine(rule) ? SccGui.ACCENT : SccGui.MUTED;
                SccGui.outline(g, pos[0], pos[1], CELL - 1, CELL - 1, color);
                String mark = rule.kind() == SlotRule.Kind.EXPORT ? "E" : rule.kind() == SlotRule.Kind.IMPORT_ITEM ? "I"
                        : rule.kind() == SlotRule.Kind.IMPORT_FLUID ? "F" : "B";
                g.pose().pushPose();
                g.pose().translate(0.0F, 0.0F, 300.0F);
                g.drawString(this.font, mark, pos[0] + 2, pos[1] + 1, color, true);
                g.pose().popPose();
                if (over && this.editing < 0) {
                    tip = describe(rule);
                }
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (this.editing >= 0) {
            this.renderPopup(g, mouseX, mouseY);
        } else if (tip != null) {
            SccGui.tooltip(g, this.font, tip, mouseX, mouseY);
        }
    }

    private static List<Component> describe(SlotRule rule) {
        List<Component> lines = new ArrayList<>();
        switch (rule.kind()) {
            case EXPORT -> {
                lines.add(Component.literal("Keeps this slot stocked"));
                StackKey key = rule.hasItem() ? StackKey.decode(rule.item()) : null;
                if (key != null) {
                    lines.add(Component.literal(rule.want() + "x " + key.display().getHoverName().getString()));
                }
                if (rule.hasFluid()) {
                    lines.add(Component.literal("Fluid: " + rule.fluid()));
                }
                lines.add(Component.literal(rule.craft() ? "When empty: craft " + rule.craftAmount() : "When empty: nothing"));
            }
            case IMPORT_ITEM -> lines.add(Component.literal("Sends the items to the system"));
            case IMPORT_FLUID -> lines.add(Component.literal("Pours the fluid into the system, keeps the container"));
            case IMPORT_BOTH -> lines.add(Component.literal("Pours the fluid, then sends the container"));
        }
        return lines;
    }

    private void renderPopup(GuiGraphics g, int mouseX, int mouseY) {
        int px = this.popupX();
        int py = this.popupY();
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 350.0F);
        g.fill(px - 3, py - 3, px + this.popupW() + 3, py + this.popupH() + 3, SccGui.BORDER);
        g.fill(px, py, px + this.popupW(), py + this.popupH(), SccGui.PANEL);
        g.drawString(this.font, Component.literal("Slot " + slotName(this.editing)), px + 8, py + 8, 0xFFFFFF, false);
        if (this.mode == Mode.IMPORT) {
            g.drawString(this.font, Component.literal("What should this slot send?"), px + 8, py + 20, 0xAAAAAA, false);
            g.pose().popPose();
            return;
        }
        // Chosen item / fluid chips.
        StackKey key = this.draftItem.isBlank() ? null : StackKey.decode(this.draftItem);
        g.fill(px + 8, py + 22, px + 106, py + 44, SccGui.PLATE_BG);
        g.fill(px + 108, py + 22, px + 206, py + 44, SccGui.PLATE_BG);
        g.drawString(this.font, "Item", px + 10, py + 11 + 0, 0x888888, false);
        if (key != null) {
            g.renderItem(key.display(), px + 10, py + 25);
            g.drawString(this.font, this.font.plainSubstrByWidth(key.display().getHoverName().getString(), 60), px + 30, py + 29, 0xFFFFFF, false);
        } else {
            g.drawString(this.font, "none", px + 12, py + 29, 0x777777, false);
        }
        g.drawString(this.font, "Fluid", px + 110, py + 11, 0x888888, false);
        if (!this.draftFluid.isBlank()) {
            g.drawString(this.font, this.font.plainSubstrByWidth(this.draftFluid, 90), px + 112, py + 29, 0xFFFFFF, false);
        } else {
            g.drawString(this.font, "none", px + 112, py + 29, 0x777777, false);
        }
        // Results grid.
        int rx = this.resultsX();
        int ry = this.resultsY();
        Choice hovered = null;
        for (int row = 0; row < RESULT_ROWS; row++) {
            for (int col = 0; col < RESULT_COLS; col++) {
                int index = (this.resultScroll + row) * RESULT_COLS + col;
                int x = rx + col * CELL;
                int y = ry + row * CELL;
                g.fill(x, y, x + CELL - 1, y + CELL - 1, SccGui.PLATE_BG);
                if (index < this.results.size()) {
                    Choice choice = this.results.get(index);
                    g.renderItem(choice.icon(), x + 1, y + 1);
                    if (choice.fluid() != null) {
                        g.fill(x, y + CELL - 3, x + CELL - 1, y + CELL - 1, 0xFF3A7BD5);
                    }
                    boolean chosen = choice.fluid() != null
                            ? choice.id().equals(this.draftFluid)
                            : key != null && choice.item() == key.item();
                    if (chosen) {
                        SccGui.outline(g, x, y, CELL - 1, CELL - 1, SccGui.ACCENT);
                    }
                    if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                        hovered = choice;
                    }
                }
            }
        }
        int below = this.resultsY() + RESULT_ROWS * CELL + 6;
        g.drawString(this.font, "Amount", px + 8, below + 4, 0xAAAAAA, false);
        g.pose().popPose();
        if (hovered != null) {
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 400.0F);
            SccGui.tooltip(g, this.font, List.of(Component.literal(hovered.name()), Component.literal(hovered.id())), mouseX, mouseY);
            g.pose().popPose();
        }
    }

    private static String slotName(int slot) {
        if (slot >= 0 && slot <= 8) {
            return "hotbar " + (slot + 1);
        }
        if (slot <= 35) {
            return "inventory " + (slot - 8);
        }
        return switch (slot) {
            case 36 -> "boots";
            case 37 -> "leggings";
            case 38 -> "chestplate";
            case 39 -> "helmet";
            default -> "off hand";
        };
    }
}
