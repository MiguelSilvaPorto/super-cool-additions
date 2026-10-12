package com.sccadd.village.client;

import com.sccadd.village.VillageChamberBlockEntity;
import com.sccadd.village.VillageConfigPayload;
import com.sccadd.village.VillageMenu;
import de.maxhenkel.easyvillagers.datacomponents.VillagerData;
import de.maxhenkel.easyvillagers.entity.EasyVillagerEntity;
import de.scc.calculator.client.screen.SccContainerScreen;
import de.scc.calculator.client.screen.SccGui;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The chamber's window, in SCC's style and laid out like Easy Villagers': the villager in 3D on the
 * left with its workstation and four buttons (the fourth, Cycle, opens the saved villagers), the offers
 * on the right with a button to trade each one, paid from the network, and where results and
 * experience go along the bottom.
 */
public final class VillageScreen extends SccContainerScreen<VillageMenu> {
    private static final int ROW_H = 24;
    private static final int ROWS = 6;
    private static final int LEFT_W = 98;
    private static final int POP_ROW = 38;
    private static final int POP_ROWS = 4;

    private int scroll;
    private boolean cycleOpen;
    private int popScroll;
    private int idleTicks;
    private final Map<VillageChamberBlockEntity.Setup, Card> cards = new IdentityHashMap<>();

    /** What the Cycle window shows of a saved villager. */
    private record Card(String profession, int level, List<ItemStack> results) {
    }

    public VillageScreen(VillageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 304;
        this.imageHeight = 244;
    }

    @Override
    protected boolean hasRail() {
        return false;
    }

    @Nullable
    private VillageChamberBlockEntity be() {
        return Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(this.menu.pos()) instanceof VillageChamberBlockEntity be ? be : null;
    }

    private void send(int action, int value, int extra) {
        PacketDistributor.sendToServer(new VillageConfigPayload(this.menu.pos(), action, value, extra));
    }

    // ---- layout ----

    private int villagerX() {
        return this.leftPos + 8;
    }

    private int villagerY() {
        return this.topPos + 20;
    }

    private int benchY() {
        return this.topPos + 138;
    }

    private int buttonY(int index) {
        return this.topPos + 164 + index * 19;
    }

    private int listX() {
        return this.leftPos + 112;
    }

    private int listY() {
        return this.topPos + 34;
    }

    private int bottomY() {
        return this.listY() + ROWS * ROW_H + 6;
    }

    private int popX() {
        return this.leftPos + 24;
    }

    private int popY() {
        return this.topPos + 26;
    }

    private int popW() {
        return this.imageWidth - 48;
    }

    private int popH() {
        return 34 + POP_ROWS * POP_ROW + 8;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---- ticking ----

    @Override
    protected void containerTick() {
        super.containerTick();
        VillageChamberBlockEntity be = this.be();
        // a villager with a bench but no offers on this side: ask the server to make and send them
        if (be != null && be.villager() != null && be.offers().isEmpty() && !"none".equals(be.workstationName())
                && ++this.idleTicks >= 20) {
            this.idleTicks = 0;
            this.send(VillageConfigPayload.REFRESH, 0, 0);
        }
    }

    // ---- input ----

    @Override
    protected boolean sccMouseClicked(double mx, double my, int button) {
        VillageChamberBlockEntity be = this.be();
        if (be == null || button != 0) {
            return super.sccMouseClicked(mx, my, button);
        }
        if (this.cycleOpen) {
            return this.clickCycle(be, mx, my);
        }
        MerchantOffers offers = be.offers();
        for (int row = 0; row < ROWS; row++) {
            int index = this.scroll + row;
            if (index >= offers.size()) {
                break;
            }
            int y = this.listY() + row * ROW_H;
            if (in(mx, my, this.listX() + 124, y + 2, 48, ROW_H - 6)) {
                this.send(VillageConfigPayload.TRADE, index, Screen.hasControlDown() ? 64 : Screen.hasShiftDown() ? 10 : 1);
                return true;
            }
        }
        int x = this.villagerX();
        if (in(mx, my, x, this.buttonY(0), LEFT_W, 16)) {
            this.send(VillageConfigPayload.TAKE_VILLAGER, 0, 0);
            return true;
        }
        if (in(mx, my, x, this.buttonY(1), LEFT_W, 16)) {
            this.send(VillageConfigPayload.TAKE_WORKSTATION, 0, 0);
            return true;
        }
        if (in(mx, my, x, this.buttonY(2), LEFT_W, 16)) {
            this.send(VillageConfigPayload.REMOVE_MODULE, 0, 0);
            return true;
        }
        if (in(mx, my, x, this.buttonY(3), LEFT_W, 16)) {
            this.cycleOpen = true;
            this.popScroll = 0;
            return true;
        }
        int by = this.bottomY();
        if (in(mx, my, this.listX(), by + 4, 180, 16)) {
            this.send(VillageConfigPayload.OUTPUT, be.output().ordinal() + 1, 0);
            return true;
        }
        if (in(mx, my, this.listX(), by + 22, 180, 16)) {
            this.send(VillageConfigPayload.XP_MODE, be.xpMode().ordinal() + 1, 0);
            return true;
        }
        return super.sccMouseClicked(mx, my, button);
    }

    private boolean clickCycle(VillageChamberBlockEntity be, double mx, double my) {
        int px = this.popX();
        int py = this.popY();
        if (!in(mx, my, px, py, this.popW(), this.popH())) {
            this.cycleOpen = false;
            return true;
        }
        if (in(mx, my, px + this.popW() - 92, py + 15, 86, 16)) {
            this.send(VillageConfigPayload.SAVE_SETUP, 0, 0);
            return true;
        }
        List<VillageChamberBlockEntity.Setup> setups = be.setups();
        for (int row = 0; row < POP_ROWS; row++) {
            int index = this.popScroll + row;
            if (index >= setups.size()) {
                break;
            }
            int y = py + 30 + row * POP_ROW;
            if (in(mx, my, px + this.popW() - 92, y + 6, 46, 16)) {
                this.send(VillageConfigPayload.USE_SETUP, index, 0);
                return true;
            }
            if (in(mx, my, px + this.popW() - 42, y + 6, 34, 16)) {
                this.send(VillageConfigPayload.DELETE_SETUP, index, 0);
                return true;
            }
        }
        return true;
    }

    @Override
    protected boolean sccMouseScrolled(double mx, double my, double dx, double dy) {
        VillageChamberBlockEntity be = this.be();
        if (this.cycleOpen) {
            int size = be == null ? 0 : be.setups().size();
            this.popScroll = Math.max(0, Math.min(Math.max(0, size - POP_ROWS), this.popScroll - (int) Math.signum(dy)));
            return true;
        }
        int size = be == null ? 0 : be.offers().size();
        this.scroll = Math.max(0, Math.min(Math.max(0, size - ROWS), this.scroll - (int) Math.signum(dy)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == 256 && this.cycleOpen) {
            this.cycleOpen = false;
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // ---- drawing ----

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        SccGui.panel(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, SccGui.BG);
        SccGui.header(g, this.leftPos + 1, this.topPos + 1, this.imageWidth - 2, 14);
    }

    @Override
    protected void sccRender(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.sccRender(g, mouseX, mouseY, partialTick);
        VillageChamberBlockEntity be = this.be();
        String kind = be == null ? "" : switch (be.module()) {
            case TRADER -> "Trader";
            case AUTO -> "Auto Trader";
            default -> "";
        };
        g.drawString(this.font, "Village Chamber" + (kind.isEmpty() ? "" : "  ·  " + kind), this.leftPos + 8, this.topPos + 4, SccGui.ACCENT, false);
        if (be == null) {
            return;
        }
        boolean under = this.cycleOpen;
        this.drawVillager(g, be, under ? -1 : mouseX, under ? -1 : mouseY);
        this.drawOffers(g, be, under ? -1 : mouseX, under ? -1 : mouseY);
        this.drawBottom(g, be, under ? -1 : mouseX, under ? -1 : mouseY);
        if (this.cycleOpen) {
            this.drawCycle(g, be, mouseX, mouseY);
        }
    }

    private void drawVillager(GuiGraphics g, VillageChamberBlockEntity be, int mouseX, int mouseY) {
        int x = this.villagerX();
        int y = this.villagerY();
        g.fill(x, y, x + LEFT_W, y + 114, SccGui.PLATE_BG);
        SccGui.outline(g, x, y, LEFT_W, 114, SccGui.BORDER);
        EasyVillagerEntity villager = be.villager();
        if (villager == null) {
            g.drawString(this.font, "No villager inside", x + 8, y + 50, SccGui.MUTED, false);
            g.drawString(this.font, "Use a villager item", x + 8, y + 62, SccGui.MUTED, false);
            g.drawString(this.font, "on the block.", x + 8, y + 72, SccGui.MUTED, false);
        } else {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 1, y + 1, x + LEFT_W - 1, y + 84, 36, 0.0625F,
                    mouseX < 0 ? x + LEFT_W / 2.0F : mouseX, mouseY < 0 ? y + 40 : mouseY, villager);
            net.minecraft.world.entity.npc.VillagerData data = villager.getVillagerData();
            String profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.getProfession()).getPath().replace('_', ' ');
            g.drawString(this.font, this.font.plainSubstrByWidth(profession + "  lvl " + data.getLevel(), LEFT_W - 6), x + 4, y + 88, SccGui.TEXT, false);
            int level = data.getLevel();
            boolean max = !net.minecraft.world.entity.npc.VillagerData.canLevelUp(level);
            int from = net.minecraft.world.entity.npc.VillagerData.getMinXpPerLevel(level);
            int to = net.minecraft.world.entity.npc.VillagerData.getMaxXpPerLevel(level);
            float fill = max ? 1.0F : Math.max(0.0F, Math.min(1.0F, (villager.getVillagerXp() - from) / (float) Math.max(1, to - from)));
            g.fill(x + 4, y + 100, x + LEFT_W - 4, y + 106, 0xFF10161C);
            g.fill(x + 4, y + 100, x + 4 + (int) ((LEFT_W - 8) * fill), y + 106, SccGui.GOOD);
            if (mouseX >= x + 4 && mouseX < x + LEFT_W - 4 && mouseY >= y + 98 && mouseY < y + 108) {
                SccGui.tooltip(g, this.font, Component.literal(max ? "Top level" : "Experience " + villager.getVillagerXp() + " / " + to), mouseX, mouseY);
            }
        }
        // the workstation, as the block it is
        int by = this.benchY();
        g.fill(x, by, x + LEFT_W, by + 22, SccGui.PLATE_BG);
        SccGui.outline(g, x, by, LEFT_W, 22, SccGui.BORDER);
        String bench = be.workstationName();
        ItemStack benchStack = benchStack(be);
        if (!benchStack.isEmpty()) {
            g.renderItem(benchStack, x + 3, by + 3);
        }
        g.drawString(this.font, this.font.plainSubstrByWidth("none".equals(bench) ? "No workstation" : bench, LEFT_W - 28), x + 24, by + 7,
                "none".equals(bench) ? SccGui.WARN : SccGui.TEXT, false);
        this.button(g, x, this.buttonY(0), LEFT_W, "Take villager out", mouseX, mouseY);
        this.button(g, x, this.buttonY(1), LEFT_W, "Take bench out", mouseX, mouseY);
        this.button(g, x, this.buttonY(2), LEFT_W, "Remove module", mouseX, mouseY);
        this.button(g, x, this.buttonY(3), LEFT_W, "Cycle  (" + be.setups().size() + ")", mouseX, mouseY);
        if (in(mouseX, mouseY, x, this.buttonY(3), LEFT_W, 16)) {
            SccGui.tooltip(g, this.font, List.of(Component.literal("Saved villagers and their workstations."),
                    Component.literal("Swap to another one in a click.")), mouseX, mouseY);
        }
    }

    private static ItemStack benchStack(VillageChamberBlockEntity be) {
        var module = be.inner();
        if (module == null || !module.hasWorkstation()) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(module.getWorkstation());
    }

    private void drawOffers(GuiGraphics g, VillageChamberBlockEntity be, int mouseX, int mouseY) {
        MerchantOffers offers = be.offers();
        g.drawString(this.font, "Trades", this.listX(), this.topPos + 21, SccGui.TEXT, false);
        g.drawString(this.font, offers.size() + " offer(s)", this.listX() + 124, this.topPos + 21, SccGui.MUTED, false);
        if (offers.isEmpty()) {
            String why = be.villager() == null ? "Put a villager in the chamber."
                    : "none".equals(be.workstationName()) ? "Add a workstation: a villager only trades with one."
                    : "Asking the server for the trades...";
            g.drawString(this.font, why, this.listX() + 4, this.listY() + 8, SccGui.MUTED, false);
        }
        for (int row = 0; row < ROWS; row++) {
            int index = this.scroll + row;
            if (index >= offers.size()) {
                break;
            }
            MerchantOffer offer = offers.get(index);
            int x = this.listX();
            int y = this.listY() + row * ROW_H;
            g.fill(x, y, x + 174, y + ROW_H - 2, SccGui.PLATE_BG);
            this.item(g, offer.getCostA(), x + 4, y + 3, mouseX, mouseY);
            if (!offer.getCostB().isEmpty()) {
                g.drawString(this.font, "+", x + 26, y + 8, SccGui.MUTED, false);
                this.item(g, offer.getCostB(), x + 34, y + 3, mouseX, mouseY);
            }
            g.drawString(this.font, "→", x + 58, y + 8, SccGui.MUTED, false);
            this.item(g, offer.getResult(), x + 70, y + 3, mouseX, mouseY);
            boolean out = offer.isOutOfStock();
            boolean hover = in(mouseX, mouseY, x + 124, y + 2, 48, ROW_H - 6);
            g.fill(x + 124, y + 2, x + 172, y + ROW_H - 4, out ? 0xFF3A2222 : (hover ? SccGui.ROW_HOVER : 0xFF10161C));
            SccGui.outline(g, x + 124, y + 2, 48, ROW_H - 6, out ? SccGui.BAD : (hover ? SccGui.ACCENT : SccGui.BORDER));
            g.drawString(this.font, out ? "sold out" : "Trade", x + (out ? 129 : 136), y + 8, out ? SccGui.WARN : SccGui.TEXT, false);
            if (hover && !out) {
                SccGui.tooltip(g, this.font, List.of(Component.literal("Trade once, paid from the network."),
                        Component.literal("Shift: 10 times   Ctrl: 64 times")), mouseX, mouseY);
            }
        }
        SccGui.verticalScrollbar(g, this.listX() + 176, this.listY(), ROWS * ROW_H, offers.size(), 1, ROWS, this.scroll);
    }

    private void drawBottom(GuiGraphics g, VillageChamberBlockEntity be, int mouseX, int mouseY) {
        int by = this.bottomY();
        g.fill(this.listX() - 2, by - 3, this.leftPos + this.imageWidth - 6, by - 2, SccGui.BORDER);
        String where = switch (be.output()) {
            case NETWORK -> "network";
            case NEARBY -> "nearby, then network";
            case PLAYER -> "to me";
        };
        this.button(g, this.listX(), by + 4, 180, "Results go to: " + where, mouseX, mouseY);
        String xp = switch (be.xpMode()) {
            case AUTO -> "fluid if a mod has it, else orbs";
            case ORBS -> "orbs";
            case OFF -> "none";
        };
        this.button(g, this.listX(), by + 22, 180, "Experience: " + xp, mouseX, mouseY);
        if (in(mouseX, mouseY, this.listX(), by + 22, 180, 16)) {
            SccGui.tooltip(g, this.font, List.of(Component.literal("What a trade's experience becomes."),
                    Component.literal("auto: experience fluid in the network if a mod adds one, orbs otherwise")), mouseX, mouseY);
        }
        SccGui.clipped(g, this.font, be.stateText(), this.listX(), by + 42, 182, SccGui.MUTED);
    }

    private void drawCycle(GuiGraphics g, VillageChamberBlockEntity be, int mouseX, int mouseY) {
        g.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xB0000000);
        int px = this.popX();
        int py = this.popY();
        int pw = this.popW();
        SccGui.panel(g, px, py, pw, this.popH(), SccGui.BG);
        SccGui.header(g, px + 1, py + 1, pw - 2, 14);
        g.drawString(this.font, "Cycle  ·  saved villagers", px + 8, py + 4, SccGui.ACCENT, false);
        this.button(g, px + pw - 92, py + 15, 86, "Save current", mouseX, mouseY);
        List<VillageChamberBlockEntity.Setup> setups = be.setups();
        if (setups.isEmpty()) {
            g.drawString(this.font, "Nothing saved yet. Save the villager in the chamber,", px + 10, py + 40, SccGui.MUTED, false);
            g.drawString(this.font, "then put another one in and cycle between them.", px + 10, py + 52, SccGui.MUTED, false);
        }
        for (int row = 0; row < POP_ROWS; row++) {
            int index = this.popScroll + row;
            if (index >= setups.size()) {
                break;
            }
            VillageChamberBlockEntity.Setup setup = setups.get(index);
            Card card = this.cards.computeIfAbsent(setup, VillageScreen::card);
            int y = py + 30 + row * POP_ROW + 16;
            int x = px + 8;
            g.fill(x, y - 14 + 0, px + pw - 8, y - 14 + POP_ROW - 2, SccGui.PLATE_BG);
            if (setup.bench() != null) {
                BuiltInRegistries.ITEM.getOptional(setup.bench()).ifPresent(item -> g.renderItem(new ItemStack(item), x + 4, y - 11));
            }
            g.drawString(this.font, this.font.plainSubstrByWidth(card.profession() + "  lvl " + card.level(), 120), x + 26, y - 10, SccGui.TEXT, false);
            int ix = x + 26;
            for (int i = 0; i < Math.min(5, card.results().size()); i++) {
                g.renderItem(card.results().get(i), ix, y - 1);
                ix += 17;
            }
            boolean useHover = in(mouseX, mouseY, px + pw - 92, y - 8, 46, 16);
            g.fill(px + pw - 92, y - 8, px + pw - 46, y + 8, useHover ? SccGui.ROW_HOVER : 0xFF10161C);
            SccGui.outline(g, px + pw - 92, y - 8, 46, 16, useHover ? SccGui.ACCENT : SccGui.BORDER);
            g.drawString(this.font, "Use", px + pw - 76, y - 4, SccGui.TEXT, false);
            boolean delHover = in(mouseX, mouseY, px + pw - 42, y - 8, 34, 16);
            g.fill(px + pw - 42, y - 8, px + pw - 8, y + 8, delHover ? 0xFF3A2222 : 0xFF10161C);
            SccGui.outline(g, px + pw - 42, y - 8, 34, 16, delHover ? SccGui.BAD : SccGui.BORDER);
            g.drawString(this.font, "Take", px + pw - 38, y - 4, SccGui.TEXT, false);
            if (delHover) {
                SccGui.tooltip(g, this.font, Component.literal("Take this villager and its bench out of the list"), mouseX, mouseY);
            }
        }
        SccGui.verticalScrollbar(g, px + pw - 6, py + 30, POP_ROWS * POP_ROW, setups.size(), 1, POP_ROWS, this.popScroll);
        g.drawString(this.font, "Esc or click outside to close", px + 8, py + this.popH() - 11, SccGui.MUTED, false);
    }

    private static Card card(VillageChamberBlockEntity.Setup setup) {
        String profession = "villager";
        int level = 1;
        List<ItemStack> results = new ArrayList<>();
        try {
            if (Minecraft.getInstance().level != null) {
                EasyVillagerEntity villager = VillagerData.createEasyVillager(setup.villager(), Minecraft.getInstance().level);
                net.minecraft.world.entity.npc.VillagerData data = villager.getVillagerData();
                ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.getProfession());
                profession = key.getPath().replace('_', ' ');
                level = data.getLevel();
                try {
                    for (MerchantOffer offer : villager.getOffers()) {
                        results.add(offer.getResult());
                    }
                } catch (IllegalStateException noOffersYet) {
                    // saved before its offers were made
                }
            }
        } catch (RuntimeException ignored) {
            // show what we have
        }
        return new Card(profession, level, results);
    }

    private void button(GuiGraphics g, int x, int y, int w, String label, int mx, int my) {
        boolean hover = in(mx, my, x, y, w, 16);
        g.fill(x, y, x + w, y + 16, hover ? SccGui.ROW_HOVER : SccGui.PLATE_BG);
        SccGui.outline(g, x, y, w, 16, hover ? SccGui.ACCENT : SccGui.BORDER);
        g.drawString(this.font, this.font.plainSubstrByWidth(label, w - 8), x + 5, y + 4, SccGui.TEXT, false);
    }

    private void item(GuiGraphics g, ItemStack stack, int x, int y, int mx, int my) {
        if (stack.isEmpty()) {
            return;
        }
        g.renderItem(stack, x, y);
        g.renderItemDecorations(this.font, stack, x, y);
        if (in(mx, my, x, y, 16, 16)) {
            g.renderTooltip(this.font, stack, mx, my);
        }
    }
}
