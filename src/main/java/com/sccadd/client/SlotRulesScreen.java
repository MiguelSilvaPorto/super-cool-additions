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
import de.scc.calculator.client.screen.SearchBox;
import com.sccadd.slots.SlotStock;
import com.sccadd.slots.SlotStockClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.client.renderer.Rect2i;
import net.neoforged.neoforge.fluids.FluidStack;
import de.scc.calculator.client.screen.SccContainerScreen;
import de.scc.calculator.client.screen.ViewerAreas;
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
public final class SlotRulesScreen extends SccContainerScreen<SlotRulesScreen.Menu> implements ViewerAreas.Ghost {
    public enum Mode {
        EXPORT, IMPORT
    }

    private static final int CELL = 18;
    private static final int RESULT_COLS = 9;
    private static final int RESULT_ROWS = 4;

    /** A menu with no slots: it only lets the window be a real container screen, so JEI and EMI keep working. */
    public static final class Menu extends AbstractContainerMenu {
        public Menu() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    public static void open(@Nullable Screen parent, Mode mode) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setScreen(new SlotRulesScreen(new Menu(), minecraft.player.getInventory(), parent, mode));
        }
    }

    private record Choice(@Nullable Item item, @Nullable Fluid fluid, ItemStack icon, String name, String id, String key, long count) {
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
    private SearchBox search;
    private SearchBox wantBox;
    private SearchBox craftBox;
    /** true: pick from what the system holds; false: from every item and fluid of the game. */
    private boolean fromSystem = true;
    private int seenStock = -1;
    private String query = "";

    private SlotRulesScreen(Menu menu, Inventory inventory, @Nullable Screen parent, Mode mode) {
        super(menu, inventory, Component.literal(mode == Mode.EXPORT ? "Export: system -> inventory" : "Import: inventory -> system"));
        this.parent = parent;
        this.mode = mode;
        this.imageWidth = 214;
        this.imageHeight = mode == Mode.EXPORT ? 326 : 192;
        this.rules.addAll(SlotRules.of(WirelessClient.heldTerminal()));
        if (mode == Mode.EXPORT) {
            PacketDistributor.sendToServer(new SlotStock.Request());
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.editing >= 0 && this.mode == Mode.EXPORT && this.seenStock != SlotStockClient.version()) {
            this.refreshResults(this.query);
        }
    }

    @Override
    protected boolean hasRail() {
        return false;
    }

    /** Where a JEI/EMI ingredient can be dropped: the item and fluid chips of the export pop-up. */
    @Override
    public List<ViewerAreas.GhostSlot> sccGhostSlots() {
        if (this.mode != Mode.EXPORT || this.editing < 0) {
            return List.of();
        }
        List<ViewerAreas.GhostSlot> out = new ArrayList<>(2);
        out.add(new ViewerAreas.GhostSlot(new Rect2i(this.leftPos + 10, this.topPos + 140, 96, 22), stack -> {
            StackKey key = StackKey.of(stack);
            this.draftItem = key == null ? "" : key.encode();
        }));
        out.add(ViewerAreas.GhostSlot.fluid(new Rect2i(this.leftPos + 108, this.topPos + 140, 96, 22), (FluidStack fluid) -> {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            this.draftFluid = id == null ? "" : id.toString();
        }));
        return out;
    }

    // ---- layout (everything is relative to the window) ----

    private static final int GRID_X = 38;
    private static final int GRID_Y = 34;
    private static final int ARMOR_X = 10;
    private static final int POPUP_Y = 124;

    /** Screen position of a player inventory slot. */
    private int[] slotPos(int slot) {
        int gx = this.leftPos + GRID_X;
        int gy = this.topPos + GRID_Y;
        if (slot >= 9 && slot <= 35) {
            return new int[]{gx + (slot - 9) % 9 * CELL, gy + (slot - 9) / 9 * CELL};
        }
        if (slot >= 0 && slot <= 8) {
            return new int[]{gx + slot * CELL, gy + 3 * CELL + 6};
        }
        if (slot >= 36 && slot <= 39) {
            return new int[]{this.leftPos + ARMOR_X, gy + (39 - slot) * CELL};
        }
        return new int[]{this.leftPos + ARMOR_X, gy + 3 * CELL + 6};
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

    // ---- pop-up geometry ----

    private int resultsX() {
        return this.leftPos + 10;
    }

    private int resultsY() {
        return this.topPos + 184;
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
        this.query = "";
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
        this.query = query;
        this.seenStock = SlotStockClient.version();
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Choice> out = new ArrayList<>();
        if (this.fromSystem) {
            for (String line : SlotStockClient.text().split("\n")) {
                String[] parts = line.split("\t");
                if (parts.length < 3) {
                    continue;
                }
                long count;
                try {
                    count = Long.parseLong(parts[2]);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if ("F".equals(parts[0])) {
                    ResourceLocation id = ResourceLocation.tryParse(parts[1]);
                    if (id != null && BuiltInRegistries.FLUID.containsKey(id)) {
                        addFluid(out, BuiltInRegistries.FLUID.get(id), q, count);
                    }
                } else {
                    StackKey key = StackKey.decode(parts[1]);
                    if (key == null) {
                        continue;
                    }
                    ItemStack icon = key.display();
                    String name = icon.getHoverName().getString();
                    String id = BuiltInRegistries.ITEM.getKey(icon.getItem()).toString();
                    if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q) || id.contains(q)) {
                        out.add(new Choice(icon.getItem(), null, icon, name, id, key.encode(), count));
                    }
                }
            }
        } else {
            for (Fluid fluid : BuiltInRegistries.FLUID) {
                addFluid(out, fluid, q, 0L);
            }
            if (!q.isEmpty()) {
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
                        ItemStack icon = new ItemStack(item);
                        StackKey key = StackKey.of(icon);
                        out.add(new Choice(item, null, icon, icon.getHoverName().getString(), id.toString(),
                                key == null ? "" : key.encode(), 0L));
                    }
                }
            }
        }
        this.results = out;
        int rows = (out.size() + RESULT_COLS - 1) / RESULT_COLS;
        this.resultScroll = Math.max(0, Math.min(this.resultScroll, Math.max(0, rows - RESULT_ROWS)));
    }

    private static void addFluid(List<Choice> out, Fluid fluid, String q, long amount) {
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
        out.add(new Choice(null, fluid, icon, name + " (fluid)", id.toString(), "", amount));
    }

    // ---- widgets ----

    @Override
    protected void init() {
        super.init();
        this.search = null;
        this.wantBox = null;
        this.craftBox = null;
        int px = this.leftPos;
        int py = this.topPos;
        if (this.editing < 0) {
            return;
        }
        if (this.mode == Mode.IMPORT) {
            String[] labels = {"Items", "Fluid", "Both"};
            SlotRule.Kind[] kinds = {SlotRule.Kind.IMPORT_ITEM, SlotRule.Kind.IMPORT_FLUID, SlotRule.Kind.IMPORT_BOTH};
            for (int i = 0; i < 3; i++) {
                final SlotRule.Kind kind = kinds[i];
                this.addRenderableWidget(Button.builder(Component.literal((this.draftKind == kind ? "> " : "") + labels[i]), b -> {
                    this.draftKind = kind;
                    this.rebuildWidgets();
                }).bounds(px + 10 + i * 66, py + POPUP_Y + 20, 64, 20).build());
            }
            this.addRenderableWidget(Button.builder(Component.literal("Set"), b -> this.commit(false))
                    .bounds(px + 10, py + POPUP_Y + 48, 60, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> this.commit(true))
                    .bounds(px + 74, py + POPUP_Y + 48, 60, 18).build());
            this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.closePopup())
                    .bounds(px + 138, py + POPUP_Y + 48, 66, 18).build());
            return;
        }
        this.addRenderableWidget(Button.builder(Component.literal("From: " + (this.fromSystem ? "System" : "All items")), b -> {
            this.fromSystem = !this.fromSystem;
            this.resultScroll = 0;
            this.refreshResults(this.query);
            this.rebuildWidgets();
        }).bounds(px + 110, py + POPUP_Y, 94, 14).build());
        this.search = new SearchBox(this.font, px + 13, py + 168, 188, 10, Component.literal("Search"));
        this.search.setMaxLength(60);
        this.search.setValue(this.query);
        this.search.setResponder(text -> {
            this.resultScroll = 0;
            this.refreshResults(text);
        });
        this.addRenderableWidget(this.search);
        this.wantBox = new SearchBox(this.font, px + 54, py + 264, 34, 10, Component.literal("Amount"));
        this.wantBox.setValue(Integer.toString(this.draftWant));
        this.wantBox.setResponder(text -> this.draftWant = parse(text, this.draftWant));
        this.addRenderableWidget(this.wantBox);
        this.addRenderableWidget(Button.builder(Component.literal("Craft more when empty: " + (this.draftCraft ? "On" : "Off")), b -> {
            this.draftCraft = !this.draftCraft;
            this.rebuildWidgets();
        }).bounds(px + 98, py + 260, 106, 18).build());
        this.craftBox = new SearchBox(this.font, px + 54, py + 286, 34, 10, Component.literal("Craft amount"));
        this.craftBox.setValue(Integer.toString(this.draftCraftAmount));
        this.craftBox.setResponder(text -> this.draftCraftAmount = parse(text, this.draftCraftAmount));
        this.craftBox.active = this.draftCraft;
        this.craftBox.visible = this.draftCraft;
        this.addRenderableWidget(this.craftBox);
        this.addRenderableWidget(Button.builder(Component.literal("Set"), b -> this.commit(false))
                .bounds(px + 10, py + 304, 60, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Clear"), b -> this.commit(true))
                .bounds(px + 74, py + 304, 60, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.closePopup())
                .bounds(px + 138, py + 304, 66, 18).build());
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
    protected boolean sccMouseClicked(double mx, double my, int button) {
        if (this.editing >= 0) {
            for (var child : this.children()) {
                if (child.mouseClicked(mx, my, button)) {
                    this.setFocused(child);
                    if (button == 0) {
                        this.setDragging(true);
                    }
                    return true;
                }
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
                        } else if (!choice.key().isEmpty()) {
                            this.draftItem = choice.key();
                        }
                    }
                    return true;
                }
                // The chosen item / fluid chips: click to clear.
                if (my >= this.topPos + 140 && my < this.topPos + 162) {
                    if (mx >= this.leftPos + 10 && mx < this.leftPos + 106) {
                        this.draftItem = "";
                    } else if (mx >= this.leftPos + 108 && mx < this.leftPos + 204) {
                        this.draftFluid = "";
                    }
                    return true;
                }
            }
        }
        int slot = this.slotAt(mx, my);
        if (slot >= 0 && this.editing < 0 && button == 0) {
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
        if (slot >= 0 && this.editing >= 0 && button == 0) {
            this.open(slot);
            return true;
        }
        return this.editing >= 0 ? false : super.sccMouseClicked(mx, my, button);
    }

    @Override
    protected boolean sccMouseScrolled(double mx, double my, double dx, double dy) {
        if (this.editing >= 0 && this.mode == Mode.EXPORT) {
            int rows = (this.results.size() + RESULT_COLS - 1) / RESULT_COLS;
            int max = Math.max(0, rows - RESULT_ROWS);
            this.resultScroll = Math.max(0, Math.min(max, this.resultScroll - (int) Math.signum(dy)));
            return true;
        }
        return super.sccMouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256) { // escape: pop-up first, then the screen
            if (this.editing >= 0) {
                this.closePopup();
            } else {
                this.onClose();
            }
            return true;
        }
        // A focused text box owns the keys (so typing "e" does not close the inventory-like screen).
        for (EditBox box : new EditBox[]{this.search, this.wantBox, this.craftBox}) {
            if (box != null && box.isFocused()) {
                box.keyPressed(key, scan, modifiers);
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    // ---- drawing ----

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        SccGui.panel(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, SccGui.BG);
        SccGui.header(g, this.leftPos + 1, this.topPos + 1, this.imageWidth - 2, 14);
        SccGui.slotsAround(g, this.leftPos + GRID_X, this.topPos + GRID_Y, 9, 3);
        SccGui.slotsAround(g, this.leftPos + GRID_X, this.topPos + GRID_Y + 3 * CELL + 6, 9, 1);
        SccGui.slotsAround(g, this.leftPos + ARMOR_X, this.topPos + GRID_Y, 1, 4);
        SccGui.slotsAround(g, this.leftPos + ARMOR_X, this.topPos + GRID_Y + 3 * CELL + 6, 1, 1);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    protected void sccRender(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.sccRender(g, mouseX, mouseY, partialTick);
        g.drawString(this.font, this.title.getString(), this.leftPos + 8, this.topPos + 4, SccGui.ACCENT, false);
        SccGui.clipped(g, this.font, this.mode == Mode.EXPORT
                        ? "Click a slot: what it always holds. Right-click clears."
                        : "Click a slot: what it sends to the system. Right-click clears.",
                this.leftPos + 8, this.topPos + 20, this.imageWidth - 16, SccGui.MUTED);
        Minecraft minecraft = Minecraft.getInstance();
        List<Component> tip = null;
        for (int slot = 0; slot < SlotRule.SLOT_COUNT; slot++) {
            int[] pos = this.slotPos(slot);
            boolean over = mouseX >= pos[0] && mouseX < pos[0] + CELL && mouseY >= pos[1] && mouseY < pos[1] + CELL;
            if (over) {
                g.fill(pos[0] + 1, pos[1] + 1, pos[0] + CELL - 1, pos[1] + CELL - 1, SccGui.ROW_HOVER);
            }
            SlotRule rule = this.ruleOf(slot);
            ItemStack stack = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(slot);
            if (stack.isEmpty() && rule != null && this.mine(rule) && rule.hasItem()) {
                StackKey key = StackKey.decode(rule.item());
                if (key != null) {
                    g.renderItem(key.display(), pos[0] + 1, pos[1] + 1);
                    g.fill(pos[0] + 1, pos[1] + 1, pos[0] + CELL - 1, pos[1] + CELL - 1, (0x99 << 24) | (SccGui.PANEL & 0xFFFFFF));
                }
            } else if (!stack.isEmpty()) {
                g.renderItem(stack, pos[0] + 1, pos[1] + 1);
                g.renderItemDecorations(this.font, stack, pos[0] + 1, pos[1] + 1);
            }
            if (slot == this.editing) {
                SccGui.outline(g, pos[0], pos[1], CELL, CELL, SccGui.ACCENT);
            }
            if (rule != null) {
                int color = this.mine(rule) ? SccGui.ACCENT : SccGui.MUTED;
                SccGui.outline(g, pos[0], pos[1], CELL, CELL, color);
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
        g.fill(this.leftPos + 6, this.topPos + POPUP_Y - 4, this.leftPos + this.imageWidth - 6, this.topPos + POPUP_Y - 3, SccGui.BORDER);
        if (this.editing >= 0) {
            this.renderPopup(g, mouseX, mouseY);
        } else {
            g.drawString(this.font, "Pick a slot above.", this.leftPos + 10, this.topPos + POPUP_Y + 4, SccGui.MUTED, false);
            if (tip != null) {
                SccGui.tooltip(g, this.font, tip, mouseX, mouseY);
            }
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
        int px = this.leftPos;
        int py = this.topPos;
        g.drawString(this.font, Component.literal("Slot: " + slotName(this.editing)), px + 10, py + POPUP_Y + 3, SccGui.TEXT, false);
        if (this.mode == Mode.IMPORT) {
            g.drawString(this.font, "What should this slot send?", px + 10, py + POPUP_Y + 6 + 8, SccGui.MUTED, false);
            return;
        }
        StackKey key = this.draftItem.isBlank() ? null : StackKey.decode(this.draftItem);
        // Chosen item and fluid (drop a JEI ingredient on them, or pick below; click to clear).
        g.fill(px + 10, py + 140, px + 106, py + 162, SccGui.PLATE_BG);
        g.fill(px + 108, py + 140, px + 204, py + 162, SccGui.PLATE_BG);
        if (key != null) {
            g.renderItem(key.display(), px + 12, py + 143);
            g.drawString(this.font, this.font.plainSubstrByWidth(key.display().getHoverName().getString(), 60), px + 32, py + 148, SccGui.TEXT, false);
        } else {
            g.drawString(this.font, "item: none", px + 14, py + 148, SccGui.MUTED, false);
        }
        if (!this.draftFluid.isBlank()) {
            g.drawString(this.font, this.font.plainSubstrByWidth("fluid: " + this.draftFluid, 90), px + 112, py + 148, SccGui.TEXT, false);
        } else {
            g.drawString(this.font, "fluid: none", px + 112, py + 148, SccGui.MUTED, false);
        }
        this.search.renderInto(g, this.font, px + 10, py + 165, 194, 14, "Search...");
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
                    } else if (choice.count() > 1L) {
                        g.renderItemDecorations(this.font, choice.icon(), x + 1, y + 1, shortCount(choice.count()));
                    }
                    boolean chosen = choice.fluid() != null
                            ? choice.id().equals(this.draftFluid)
                            : key != null && choice.key().equals(this.draftItem);
                    if (chosen) {
                        SccGui.outline(g, x, y, CELL - 1, CELL - 1, SccGui.ACCENT);
                    }
                    if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
                        hovered = choice;
                    }
                }
            }
        }
        int rows = (this.results.size() + RESULT_COLS - 1) / RESULT_COLS;
        SccGui.verticalScrollbar(g, rx + RESULT_COLS * CELL + 2, ry, RESULT_ROWS * CELL, this.results.size(), RESULT_COLS, RESULT_ROWS, this.resultScroll);
        if (this.results.isEmpty()) {
            g.drawString(this.font, this.fromSystem && SlotStockClient.text().isEmpty()
                    ? "The system is empty or out of range." : "Nothing found.", rx + 4, ry + 4, SccGui.MUTED, false);
        }
        g.drawString(this.font, "Amount", px + 10, py + 265, SccGui.MUTED, false);
        this.wantBox.renderInto(g, this.font, px + 52, py + 261, 40, 14, "64");
        if (this.draftCraft) {
            g.drawString(this.font, "Order", px + 10, py + 287, SccGui.MUTED, false);
            this.craftBox.renderInto(g, this.font, px + 52, py + 283, 40, 14, "64");
            g.drawString(this.font, "when it runs out", px + 96, py + 287, SccGui.MUTED, false);
        }
        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(hovered.name()));
            lines.add(Component.literal(hovered.id()));
            if (hovered.count() > 0L) {
                lines.add(Component.literal("In the system: " + hovered.count()));
            }
            SccGui.tooltip(g, this.font, lines, mouseX, mouseY);
        }
    }

    private static String shortCount(long count) {
        if (count >= 1_000_000_000L) {
            return count / 1_000_000_000L + "G";
        }
        if (count >= 1_000_000L) {
            return count / 1_000_000L + "M";
        }
        if (count >= 1_000L) {
            return count / 1_000L + "k";
        }
        return Long.toString(count);
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
