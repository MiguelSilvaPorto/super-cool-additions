package com.sccadd.village.client;

import com.sccadd.village.VillageChamberBlockEntity;
import com.sccadd.village.VillageConfigPayload;
import com.sccadd.village.VillageMenu;
import com.sccadd.village.VillageView;
import de.maxhenkel.easyvillagers.entity.EasyVillagerEntity;
import de.scc.calculator.client.screen.SccContainerScreen;
import de.scc.calculator.client.screen.SccGui;
import de.scc.calculator.client.screen.SccTextInput;
import de.scc.calculator.network.SccTier;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The chamber's window, in SCC's style and laid out like Easy Villagers': the villager in 3D on the left
 * with its name, workstation and four buttons (the fourth, Cycle, opens the villagers and workstations
 * waiting in the system), the offers on the right with a button to trade each one, paid from the network,
 * and where results and experience go along the bottom.
 *
 * <p>Everything it shows comes from the {@link VillageView} the server sends; the only thing built here is
 * the 3D villager.</p>
 */
public final class VillageScreen extends SccContainerScreen<VillageMenu> implements SccTextInput {
    private static final int ROW_H = 24;
    private static final int ROWS = 6;
    private static final int LEFT_W = 98;
    private static final int CARD_H = 50;
    private static final int CARDS = 3;
    private static final int BENCHES = 12;

    private VillageView view = VillageView.EMPTY;
    private int scroll;
    private boolean cycleOpen;
    private int cardScroll;
    private int benchScroll;
    private int refreshIn;
    private boolean naming;
    private int keepEditing = -1;
    private String typed = "";

    public VillageScreen(VillageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 304;
        this.imageHeight = 248;
    }

    public BlockPos chamber() {
        return this.menu.pos();
    }

    public void setView(VillageView view) {
        this.view = view;
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, view.offers().size() - ROWS)));
        this.cardScroll = Math.max(0, Math.min(this.cardScroll, Math.max(0, view.villagers().size() - CARDS)));
    }

    @Override
    protected boolean hasRail() {
        return false;
    }

    @Override
    public boolean sccTyping() {
        return this.naming;
    }

    private void send(int action, int value, int extra, String text) {
        PacketDistributor.sendToServer(new VillageConfigPayload(this.menu.pos(), action, value, extra, text));
    }

    @Nullable
    private EasyVillagerEntity entity() {
        return Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(this.menu.pos()) instanceof VillageChamberBlockEntity be ? be.villager() : null;
    }

    // ---- layout ----

    private int vx() {
        return this.leftPos + 8;
    }

    private int vy() {
        return this.topPos + 20;
    }

    private int benchY() {
        return this.topPos + 140;
    }

    private int buttonY(int index) {
        return this.topPos + 168 + index * 19;
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
        return this.leftPos + 16;
    }

    private int popY() {
        return this.topPos + 20;
    }

    private int popW() {
        return this.imageWidth - 32;
    }

    private int popH() {
        return this.imageHeight - 40;
    }

    private int cardsY() {
        return this.popY() + 36;
    }

    private int stripY() {
        return this.cardsY() + CARDS * CARD_H + 16;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---- ticking ----

    @Override
    protected void containerTick() {
        super.containerTick();
        if (--this.refreshIn <= 0) {
            this.refreshIn = 20;
            this.send(VillageConfigPayload.REFRESH, 0, 0, "");
        }
    }

    // ---- input ----

    @Override
    protected boolean sccMouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.sccMouseClicked(mx, my, button);
        }
        if (this.cycleOpen) {
            return this.clickCycle(mx, my);
        }
        if (this.naming && !in(mx, my, this.vx(), this.vy() + 84, LEFT_W, 12)) {
            this.commitName();
        }
        boolean clickedKeep = false;
        for (int row = 0; row < ROWS; row++) {
            int index = this.scroll + row;
            if (index >= this.view.offers().size()) {
                break;
            }
            int y = this.listY() + row * ROW_H;
            if (in(mx, my, this.listX() + 92, y + 2, 30, ROW_H - 6)) {
                if (this.keepEditing >= 0 && this.keepEditing != index) {
                    this.commitKeep();
                }
                this.keepEditing = index;
                int keep = index < this.view.keeps().size() ? this.view.keeps().get(index) : 0;
                this.typed = keep == 0 ? "" : String.valueOf(keep);
                clickedKeep = true;
                break;
            }
            if (in(mx, my, this.listX() + 124, y + 2, 48, ROW_H - 6)) {
                if (this.keepEditing >= 0) {
                    this.commitKeep();
                }
                this.send(VillageConfigPayload.TRADE, index, Screen.hasControlDown() ? 64 : Screen.hasShiftDown() ? 10 : 1, "");
                return true;
            }
        }
        if (!clickedKeep && this.keepEditing >= 0) {
            this.commitKeep();
        }
        int x = this.vx();
        if (this.view.hasVillager() && in(mx, my, x, this.vy() + 84, LEFT_W, 12)) {
            this.naming = true;
            this.typed = this.view.name();
            return true;
        }
        if (in(mx, my, x, this.buttonY(0), LEFT_W, 16)) {
            this.send(VillageConfigPayload.TAKE_VILLAGER, 0, 0, "");
            return true;
        }
        if (in(mx, my, x, this.buttonY(1), LEFT_W, 16)) {
            this.send(VillageConfigPayload.TAKE_WORKSTATION, 0, 0, "");
            return true;
        }
        if (in(mx, my, x, this.buttonY(2), LEFT_W, 16)) {
            this.send(VillageConfigPayload.REMOVE_MODULE, 0, 0, "");
            return true;
        }
        if (in(mx, my, x, this.buttonY(3), LEFT_W, 16)) {
            this.cycleOpen = true;
            this.cardScroll = 0;
            this.refreshIn = 0;
            return true;
        }
        int by = this.bottomY();
        if (in(mx, my, this.listX(), by + 4, 180, 16)) {
            this.send(VillageConfigPayload.OUTPUT, this.view.output() + 1, 0, "");
            return true;
        }
        if (in(mx, my, this.listX(), by + 22, 180, 16)) {
            this.send(VillageConfigPayload.XP_MODE, this.view.xpMode() + 1, 0, "");
            return true;
        }
        return super.sccMouseClicked(mx, my, button);
    }

    private boolean clickCycle(double mx, double my) {
        int px = this.popX();
        int py = this.popY();
        int pw = this.popW();
        if (!in(mx, my, px, py, pw, this.popH())) {
            this.cycleOpen = false;
            return true;
        }
        if (in(mx, my, px + pw - 128, py + 18, 120, 16)) {
            this.send(VillageConfigPayload.STORE_CURRENT, 0, 0, "");
            return true;
        }
        List<VillageView.Villager> villagers = this.view.villagers();
        for (int row = 0; row < CARDS; row++) {
            int index = this.cardScroll + row;
            if (index >= villagers.size()) {
                break;
            }
            int y = this.cardsY() + row * CARD_H;
            VillageView.Villager card = villagers.get(index);
            if (in(mx, my, px + pw - 92, y + 6, 40, 16)) {
                this.send(VillageConfigPayload.USE_VILLAGER, 0, 0, card.code());
                return true;
            }
            if (in(mx, my, px + pw - 48, y + 6, 40, 16)) {
                this.send(VillageConfigPayload.TAKE_FROM_SYSTEM, 0, 0, card.code());
                return true;
            }
        }
        List<VillageView.Bench> benches = this.view.benches();
        for (int i = 0; i < BENCHES; i++) {
            int index = this.benchScroll + i;
            if (index >= benches.size()) {
                break;
            }
            if (in(mx, my, px + 8 + i * 18, this.stripY() + 12, 18, 18)) {
                this.send(VillageConfigPayload.USE_BENCH, 0, 0, benches.get(index).code());
                return true;
            }
        }
        return true;
    }

    private void commitName() {
        if (this.naming) {
            this.send(VillageConfigPayload.RENAME, 0, 0, this.typed);
        }
        this.naming = false;
    }

    private void commitKeep() {
        if (this.keepEditing < 0) {
            return;
        }
        int index = this.keepEditing;
        this.keepEditing = -1;
        int amount = 0;
        try {
            amount = this.typed.isEmpty() ? 0 : Integer.parseInt(this.typed);
        } catch (NumberFormatException tooBig) {
            amount = 99_999;
        }
        this.send(VillageConfigPayload.KEEP, index, Math.max(0, Math.min(99_999, amount)), "");
    }

    /** A short number for the keep field: 1k, 25k, 99k. */
    private static String compact(int value) {
        if (value >= 1_000_000) {
            return (value / 1_000_000) + "M";
        }
        if (value >= 1_000) {
            return (value / 1_000) + "k";
        }
        return String.valueOf(value);
    }

    @Override
    protected boolean sccMouseScrolled(double mx, double my, double dx, double dy) {
        int step = -(int) Math.signum(dy);
        if (this.cycleOpen) {
            if (my >= this.stripY()) {
                this.benchScroll = Math.max(0, Math.min(Math.max(0, this.view.benches().size() - BENCHES), this.benchScroll + step));
            } else {
                this.cardScroll = Math.max(0, Math.min(Math.max(0, this.view.villagers().size() - CARDS), this.cardScroll + step));
            }
            return true;
        }
        this.scroll = Math.max(0, Math.min(Math.max(0, this.view.offers().size() - ROWS), this.scroll + step));
        return true;
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (this.keepEditing >= 0) {
            if (ch >= '0' && ch <= '9' && this.typed.length() < 5) {
                this.typed += ch;
            }
            return true;
        }
        if (this.naming && ch >= ' ' && this.typed.length() < 40) {
            this.typed += ch;
            return true;
        }
        return super.charTyped(ch, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (this.keepEditing >= 0) {
            if (key == 257 || key == 335) {
                this.commitKeep();
            } else if (key == 259) {
                if (!this.typed.isEmpty()) {
                    this.typed = this.typed.substring(0, this.typed.length() - 1);
                }
            } else if (key == 256) {
                this.keepEditing = -1;
            }
            return true;
        }
        if (this.naming) {
            if (key == 257 || key == 335) {
                this.commitName();
            } else if (key == 259) {
                if (!this.typed.isEmpty()) {
                    this.typed = this.typed.substring(0, this.typed.length() - 1);
                }
            } else if (key == 256) {
                this.naming = false;
            }
            return true;
        }
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
        String kind = switch (this.view.module()) {
            case 1 -> "Trader";
            case 2 -> "Auto Trader";
            default -> "";
        };
        // the title itself is SCC's; the module and the tier go at the right of the header
        SccTier tier = SccTier.values()[Math.max(0, Math.min(this.view.tier(), SccTier.values().length - 1))];
        String tierName = Component.translatable("scc.tier." + tier.id()).getString();
        String head = kind.isEmpty() ? tierName : kind + "  ·  " + tierName;
        g.drawString(this.font, head, this.leftPos + this.imageWidth - 8 - this.font.width(head), this.topPos + 4,
                0xFF000000 | tier.color(), false);
        int mx = this.cycleOpen ? -1 : mouseX;
        int my = this.cycleOpen ? -1 : mouseY;
        this.drawVillager(g, mx, my);
        this.drawOffers(g, mx, my);
        this.drawBottom(g, mx, my);
        if (this.cycleOpen) {
            this.drawCycle(g, mouseX, mouseY);
        }
    }

    private void drawVillager(GuiGraphics g, int mouseX, int mouseY) {
        int x = this.vx();
        int y = this.vy();
        g.fill(x, y, x + LEFT_W, y + 116, SccGui.PLATE_BG);
        SccGui.outline(g, x, y, LEFT_W, 116, SccGui.BORDER);
        VillageView view = this.view;
        if (!view.hasVillager()) {
            g.drawString(this.font, "No villager inside", x + 8, y + 50, SccGui.MUTED, false);
            g.drawString(this.font, "Use a villager item", x + 8, y + 62, SccGui.MUTED, false);
            g.drawString(this.font, "on the block.", x + 8, y + 72, SccGui.MUTED, false);
        } else {
            EasyVillagerEntity entity = this.entity();
            if (entity != null && !this.cycleOpen) {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 1, y + 1, x + LEFT_W - 1, y + 82, 36, 0.0625F,
                        mouseX < 0 ? x + LEFT_W / 2.0F : mouseX, mouseY < 0 ? y + 40 : mouseY, entity);
            }
            // the name: click it to rename
            boolean hover = in(mouseX, mouseY, x, y + 84, LEFT_W, 12);
            g.fill(x + 2, y + 84, x + LEFT_W - 2, y + 96, this.naming ? 0xFF10161C : (hover ? SccGui.ROW_HOVER : SccGui.PLATE_BG));
            if (this.naming) {
                SccGui.outline(g, x + 2, y + 84, LEFT_W - 4, 12, SccGui.ACCENT);
            }
            String shown = this.naming ? this.typed + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "")
                    : (view.name().isEmpty() ? view.profession() : view.name());
            g.drawString(this.font, this.font.plainSubstrByWidth(shown, LEFT_W - 8), x + 4, y + 86, SccGui.TEXT, false);
            if (hover && !this.naming) {
                SccGui.tooltip(g, this.font, Component.literal("Click to rename"), mouseX, mouseY);
            }
            g.drawString(this.font, this.font.plainSubstrByWidth(view.profession() + "  lvl " + view.level(), LEFT_W - 8), x + 4, y + 98, SccGui.MUTED, false);
            float fill = view.topLevel() ? 1.0F
                    : Math.max(0.0F, Math.min(1.0F, (view.xp() - view.xpFrom()) / (float) Math.max(1, view.xpTo() - view.xpFrom())));
            g.fill(x + 4, y + 108, x + LEFT_W - 4, y + 112, 0xFF10161C);
            g.fill(x + 4, y + 108, x + 4 + (int) ((LEFT_W - 8) * fill), y + 112, SccGui.GOOD);
            if (in(mouseX, mouseY, x + 4, y + 106, LEFT_W - 8, 8)) {
                SccGui.tooltip(g, this.font, Component.literal(view.topLevel() ? "Top level" : "Experience " + view.xp() + " / " + view.xpTo()), mouseX, mouseY);
            }
        }
        // the workstation, as the block it is
        int by = this.benchY();
        g.fill(x, by, x + LEFT_W, by + 22, SccGui.PLATE_BG);
        SccGui.outline(g, x, by, LEFT_W, 22, SccGui.BORDER);
        if (!view.bench().isEmpty()) {
            g.renderItem(view.bench(), x + 3, by + 3);
            g.drawString(this.font, this.font.plainSubstrByWidth(view.bench().getHoverName().getString(), LEFT_W - 28), x + 24, by + 7, SccGui.TEXT, false);
        } else {
            g.drawString(this.font, "No workstation", x + 6, by + 7, SccGui.WARN, false);
        }
        this.button(g, x, this.buttonY(0), LEFT_W, "Take villager out", mouseX, mouseY);
        this.button(g, x, this.buttonY(1), LEFT_W, "Take bench out", mouseX, mouseY);
        this.button(g, x, this.buttonY(2), LEFT_W, "Remove module", mouseX, mouseY);
        this.button(g, x, this.buttonY(3), LEFT_W, "Cycle  (" + view.villagers().size() + ")", mouseX, mouseY);
        if (in(mouseX, mouseY, x, this.buttonY(3), LEFT_W, 16)) {
            SccGui.tooltip(g, this.font, List.of(Component.literal("The villagers and workstations waiting in the system."),
                    Component.literal("Swap to another in a click.")), mouseX, mouseY);
        }
    }

    private void drawOffers(GuiGraphics g, int mouseX, int mouseY) {
        List<MerchantOffer> offers = this.view.offers();
        g.drawString(this.font, "Trades", this.listX(), this.topPos + 21, SccGui.TEXT, false);
        g.drawString(this.font, offers.size() + " offer(s)", this.listX() + 124, this.topPos + 21, SccGui.MUTED, false);
        if (offers.isEmpty()) {
            String why = !this.view.hasVillager() ? "Put a villager in the chamber."
                    : this.view.bench().isEmpty() ? "Add a workstation: a villager only trades with one."
                    : "This villager has no trades.";
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
            // the keep field: how much of this trade's result the network should always hold
            int keep = index < this.view.keeps().size() ? this.view.keeps().get(index) : 0;
            boolean editing = this.keepEditing == index;
            boolean keepHover = in(mouseX, mouseY, x + 92, y + 2, 30, ROW_H - 6);
            g.fill(x + 92, y + 2, x + 122, y + ROW_H - 4, editing ? 0xFF10161C : (keep > 0 ? 0xFF14261C : (keepHover ? SccGui.ROW_HOVER : 0xFF10161C)));
            SccGui.outline(g, x + 92, y + 2, 30, ROW_H - 6, editing || keep > 0 ? SccGui.ACCENT : SccGui.BORDER);
            String keepText = editing ? (this.typed.isEmpty() ? "_" : this.typed) : (keep > 0 ? compact(keep) : "keep");
            g.drawString(this.font, keepText, x + 92 + (30 - this.font.width(keepText)) / 2, y + 8,
                    editing || keep > 0 ? SccGui.ACCENT : SccGui.MUTED, false);
            if (keepHover && !editing) {
                SccGui.tooltip(g, this.font, List.of(Component.literal("Keep this much of it in stock."),
                        Component.literal("The chamber trades on its own to keep up: " + this.view.tradesPerSecond() + " trade(s)/s,"),
                        Component.literal("up to " + SccGui.num(this.view.keepCap()) + " per trade, paid from the network."),
                        Component.literal("Click to type an amount; 0 turns it off.")), mouseX, mouseY);
            }
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

    private void drawBottom(GuiGraphics g, int mouseX, int mouseY) {
        int by = this.bottomY();
        g.fill(this.listX() - 2, by - 3, this.leftPos + this.imageWidth - 6, by - 2, SccGui.BORDER);
        String where = switch (this.view.output()) {
            case 0 -> "network";
            case 1 -> "nearby, then network";
            default -> "to me";
        };
        this.button(g, this.listX(), by + 4, 180, "Results go to: " + where, mouseX, mouseY);
        String xp = switch (this.view.xpMode()) {
            case 0 -> "fluid if a mod has it, else orbs";
            case 1 -> "orbs";
            default -> "none";
        };
        this.button(g, this.listX(), by + 22, 180, "Experience: " + xp, mouseX, mouseY);
        if (in(mouseX, mouseY, this.listX(), by + 22, 180, 16)) {
            SccGui.tooltip(g, this.font, List.of(Component.literal("What a trade's experience becomes."),
                    Component.literal("auto: experience fluid in the network if a mod adds one, orbs otherwise")), mouseX, mouseY);
        }
        SccGui.clipped(g, this.font, this.view.state(), this.listX(), by + 42, 182, SccGui.MUTED);
    }

    private void drawCycle(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, 0xB0000000);
        int px = this.popX();
        int py = this.popY();
        int pw = this.popW();
        SccGui.panel(g, px, py, pw, this.popH(), SccGui.BG);
        SccGui.header(g, px + 1, py + 1, pw - 2, 14);
        g.drawString(this.font, "Cycle  ·  kept in the chamber (" + this.view.stored() + "/" + this.view.villagerCap() + ")", px + 8, py + 4, SccGui.ACCENT, false);
        g.drawString(this.font, "Villagers", px + 8, py + 22, SccGui.TEXT, false);
        this.button(g, px + pw - 128, py + 18, 120, "Store current", mouseX, mouseY);
        if (in(mouseX, mouseY, px + pw - 128, py + 18, 120, 16)) {
            SccGui.tooltip(g, this.font, List.of(
                    Component.literal("Puts the villager into the chamber's own stock (" + this.view.stored() + "/" + this.view.villagerCap() + ")."),
                    Component.literal(this.view.villagerEnergy() > 0
                            ? "A new one costs " + SccGui.num(this.view.villagerEnergy()) + " FE here."
                            : "Free at this tier."),
                    Component.literal("The workstation goes to the system.")), mouseX, mouseY);
        }
        List<VillageView.Villager> villagers = this.view.villagers();
        if (villagers.isEmpty()) {
            g.drawString(this.font, "No villager item in the system. Store the one in the chamber,", px + 10, this.cardsY() + 6, SccGui.MUTED, false);
            g.drawString(this.font, "or put villager items in a chest or storage on the network.", px + 10, this.cardsY() + 18, SccGui.MUTED, false);
        }
        for (int row = 0; row < CARDS; row++) {
            int index = this.cardScroll + row;
            if (index >= villagers.size()) {
                break;
            }
            this.drawCard(g, villagers.get(index), px + 8, this.cardsY() + row * CARD_H, pw - 24, mouseX, mouseY);
        }
        SccGui.verticalScrollbar(g, px + pw - 12, this.cardsY(), CARDS * CARD_H, villagers.size(), 1, CARDS, this.cardScroll);
        // workstations in the system
        int sy = this.stripY();
        g.fill(px + 6, sy - 3, px + pw - 6, sy - 2, SccGui.BORDER);
        g.drawString(this.font, "Workstations in the system - click one to swap it in", px + 8, sy, SccGui.TEXT, false);
        List<VillageView.Bench> benches = this.view.benches();
        if (benches.isEmpty()) {
            g.drawString(this.font, "none", px + 10, sy + 16, SccGui.MUTED, false);
        }
        for (int i = 0; i < BENCHES; i++) {
            int index = this.benchScroll + i;
            if (index >= benches.size()) {
                break;
            }
            VillageView.Bench bench = benches.get(index);
            int x = px + 8 + i * 18;
            boolean hover = in(mouseX, mouseY, x, sy + 12, 18, 18);
            g.fill(x, sy + 12, x + 18, sy + 30, hover ? SccGui.ROW_HOVER : SccGui.PLATE_BG);
            SccGui.outline(g, x, sy + 12, 18, 18, hover ? SccGui.ACCENT : SccGui.BORDER);
            g.renderItem(bench.stack(), x + 1, sy + 13);
            if (hover) {
                SccGui.tooltip(g, this.font, List.of(bench.stack().getHoverName(), Component.literal(bench.count() + " in the system")), mouseX, mouseY);
            }
        }
        g.drawString(this.font, "Esc or click outside to close", px + 8, py + this.popH() - 11, SccGui.MUTED, false);
    }

    /** One villager waiting in the system: its name, its level, and a preview of what it trades. */
    private void drawCard(GuiGraphics g, VillageView.Villager card, int x, int y, int w, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + CARD_H - 3, SccGui.PLATE_BG);
        String head = card.name() + "  ·  " + card.profession() + " lvl " + card.level() + (card.count() > 1 ? "  x" + card.count() : "");
        g.drawString(this.font, this.font.plainSubstrByWidth(head, w - 108), x + 6, y + 5, SccGui.TEXT, false);
        // the trades it already has, as cost -> result
        if (card.offers().isEmpty()) {
            g.drawString(this.font, "no trades made yet", x + 6, y + 24, SccGui.MUTED, false);
        }
        int ix = x + 6;
        int shown = 0;
        for (MerchantOffer offer : card.offers()) {
            if (shown >= 4) {
                g.drawString(this.font, "+" + (card.offers().size() - shown), ix, y + 28, SccGui.MUTED, false);
                break;
            }
            this.mini(g, offer.getCostA(), ix, y + 22, mouseX, mouseY);
            g.drawString(this.font, "→", ix + 14, y + 26, SccGui.MUTED, false);
            this.mini(g, offer.getResult(), ix + 22, y + 22, mouseX, mouseY);
            ix += 40;
            shown++;
        }
        int bx = x + w - 88;
        boolean useHover = in(mouseX, mouseY, bx, y + 6, 40, 16);
        g.fill(bx, y + 6, bx + 40, y + 22, useHover ? SccGui.ROW_HOVER : 0xFF10161C);
        SccGui.outline(g, bx, y + 6, 40, 16, useHover ? SccGui.ACCENT : SccGui.BORDER);
        g.drawString(this.font, "Use", bx + 12, y + 10, SccGui.TEXT, false);
        boolean takeHover = in(mouseX, mouseY, bx + 44, y + 6, 40, 16);
        g.fill(bx + 44, y + 6, bx + 84, y + 22, takeHover ? SccGui.ROW_HOVER : 0xFF10161C);
        SccGui.outline(g, bx + 44, y + 6, 40, 16, takeHover ? SccGui.ACCENT : SccGui.BORDER);
        g.drawString(this.font, "Take", bx + 54, y + 10, SccGui.TEXT, false);
        if (useHover) {
            SccGui.tooltip(g, this.font, Component.literal("Swap this villager in; the one in the chamber goes into the system"), mouseX, mouseY);
        } else if (takeHover) {
            SccGui.tooltip(g, this.font, Component.literal("Take this villager out of the system"), mouseX, mouseY);
        }
    }

    private void mini(GuiGraphics g, ItemStack stack, int x, int y, int mx, int my) {
        if (stack.isEmpty()) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
        if (in(mx, my, x, y, 12, 12)) {
            g.renderTooltip(this.font, stack, mx, my);
        }
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
