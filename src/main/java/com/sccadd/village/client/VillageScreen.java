package com.sccadd.village.client;

import com.sccadd.village.VillageChamberBlockEntity;
import com.sccadd.village.VillageConfigPayload;
import com.sccadd.village.VillageMenu;
import de.maxhenkel.easyvillagers.entity.EasyVillagerEntity;
import de.scc.calculator.client.screen.SccContainerScreen;
import de.scc.calculator.client.screen.SccGui;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The chamber's window, in SCC's style but laid out like Easy Villagers' own: the villager in 3D on the
 * left with its profession, level and workstation, the offers on the right with a button to trade each
 * one (paid from the network), and where the results go along the bottom.
 */
public final class VillageScreen extends SccContainerScreen<VillageMenu> {
    private static final int ROW_H = 24;
    private static final int ROWS = 6;

    private int scroll;

    public VillageScreen(VillageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 262;
        this.imageHeight = 40 + ROWS * ROW_H + 40;
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

    private int listX() {
        return this.leftPos + 96;
    }

    private int listY() {
        return this.topPos + 36;
    }

    private int bottomY() {
        return this.listY() + ROWS * ROW_H + 6;
    }

    // ---- input ----

    @Override
    protected boolean sccMouseClicked(double mx, double my, int button) {
        VillageChamberBlockEntity be = this.be();
        if (be == null || button != 0) {
            return super.sccMouseClicked(mx, my, button);
        }
        MerchantOffers offers = be.offers();
        for (int row = 0; row < ROWS; row++) {
            int index = this.scroll + row;
            if (index >= offers.size()) {
                break;
            }
            int y = this.listY() + row * ROW_H;
            if (mx >= this.listX() + 112 && mx < this.listX() + 158 && my >= y + 2 && my < y + ROW_H - 4) {
                int times = Screen.hasControlDown() ? 64 : Screen.hasShiftDown() ? 10 : 1;
                this.send(VillageConfigPayload.TRADE, index, times);
                return true;
            }
        }
        int left = this.leftPos + 8;
        int top = this.topPos + 140;
        if (mx >= left && mx < left + 80) {
            if (my >= top && my < top + 16) {
                this.send(VillageConfigPayload.TAKE_VILLAGER, 0, 0);
                return true;
            }
            if (my >= top + 18 && my < top + 34) {
                this.send(VillageConfigPayload.TAKE_WORKSTATION, 0, 0);
                return true;
            }
            if (my >= top + 36 && my < top + 52) {
                this.send(VillageConfigPayload.REMOVE_MODULE, 0, 0);
                return true;
            }
        }
        int by = this.bottomY();
        if (my >= by + 4 && my < by + 20 && mx >= this.listX() && mx < this.listX() + 158) {
            this.send(VillageConfigPayload.OUTPUT, be.output().ordinal() + 1, 0);
            return true;
        }
        return super.sccMouseClicked(mx, my, button);
    }

    @Override
    protected boolean sccMouseScrolled(double mx, double my, double dx, double dy) {
        VillageChamberBlockEntity be = this.be();
        int size = be == null ? 0 : be.offers().size();
        this.scroll = Math.max(0, Math.min(Math.max(0, size - ROWS), this.scroll - (int) Math.signum(dy)));
        return true;
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
            case TRADER -> " - Trader";
            case AUTO -> " - Auto Trader";
            default -> "";
        };
        g.drawString(this.font, "Village Chamber" + kind, this.leftPos + 8, this.topPos + 4, SccGui.ACCENT, false);
        if (be == null) {
            return;
        }
        this.drawVillager(g, be, mouseX, mouseY);
        this.drawOffers(g, be, mouseX, mouseY);
        this.drawBottom(g, be, mouseX, mouseY);
    }

    private void drawVillager(GuiGraphics g, VillageChamberBlockEntity be, int mouseX, int mouseY) {
        int x = this.leftPos + 8;
        int y = this.topPos + 20;
        g.fill(x, y, x + 80, y + 112, SccGui.PLATE_BG);
        SccGui.outline(g, x, y, 80, 112, SccGui.BORDER);
        EasyVillagerEntity villager = be.villager();
        if (villager == null) {
            g.drawString(this.font, "No villager", x + 16, y + 50, SccGui.MUTED, false);
            g.drawString(this.font, "inside", x + 24, y + 62, SccGui.MUTED, false);
        } else {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 1, y + 1, x + 79, y + 88, 34, 0.0625F, mouseX, mouseY, villager);
            VillagerData data = villager.getVillagerData();
            String profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.getProfession()).getPath().replace('_', ' ');
            g.drawString(this.font, this.font.plainSubstrByWidth(profession, 76), x + 3, y + 90, SccGui.TEXT, false);
            int max = VillagerData.canLevelUp(data.getLevel()) ? VillagerData.getMaxXpPerLevel(data.getLevel()) : 0;
            g.drawString(this.font, "Level " + data.getLevel() + (max > 0 ? "  " + villager.getVillagerXp() + "/" + max : ""),
                    x + 3, y + 101, SccGui.MUTED, false);
        }
        g.drawString(this.font, this.font.plainSubstrByWidth("Bench: " + be.workstationName(), 76), x, this.topPos + 134 - 0, SccGui.MUTED, false);
        int top = this.topPos + 140;
        this.button(g, x, top, 80, "Take villager out", mouseX, mouseY);
        this.button(g, x, top + 18, 80, "Take bench out", mouseX, mouseY);
        this.button(g, x, top + 36, 80, "Remove module", mouseX, mouseY);
    }

    private void drawOffers(GuiGraphics g, VillageChamberBlockEntity be, int mouseX, int mouseY) {
        MerchantOffers offers = be.offers();
        if (offers.isEmpty()) {
            g.drawString(this.font, be.villager() == null ? "Put a villager in the chamber." : "No offers: add a workstation.",
                    this.listX() + 4, this.listY() + 6, SccGui.MUTED, false);
        }
        for (int row = 0; row < ROWS; row++) {
            int index = this.scroll + row;
            if (index >= offers.size()) {
                break;
            }
            MerchantOffer offer = offers.get(index);
            int x = this.listX();
            int y = this.listY() + row * ROW_H;
            g.fill(x, y, x + 160, y + ROW_H - 2, SccGui.PLATE_BG);
            this.item(g, offer.getCostA(), x + 4, y + 3, mouseX, mouseY);
            if (!offer.getCostB().isEmpty()) {
                g.drawString(this.font, "+", x + 26, y + 8, SccGui.MUTED, false);
                this.item(g, offer.getCostB(), x + 34, y + 3, mouseX, mouseY);
            }
            g.drawString(this.font, "→", x + 58, y + 8, SccGui.MUTED, false);
            this.item(g, offer.getResult(), x + 70, y + 3, mouseX, mouseY);
            boolean out = offer.isOutOfStock();
            boolean hover = mouseX >= x + 112 && mouseX < x + 158 && mouseY >= y + 2 && mouseY < y + ROW_H - 4;
            g.fill(x + 112, y + 2, x + 158, y + ROW_H - 4, out ? 0xFF3A2222 : (hover ? SccGui.ROW_HOVER : 0xFF10161C));
            SccGui.outline(g, x + 112, y + 2, 46, ROW_H - 6, out ? SccGui.BAD : (hover ? SccGui.ACCENT : SccGui.BORDER));
            g.drawString(this.font, out ? "sold out" : "Trade", x + (out ? 117 : 123), y + 8, out ? SccGui.WARN : SccGui.TEXT, false);
            if (hover && !out) {
                SccGui.tooltip(g, this.font, List.of(Component.literal("Trade once, paid from the network."),
                        Component.literal("Shift: 10 times   Ctrl: 64 times")), mouseX, mouseY);
            }
        }
        SccGui.verticalScrollbar(g, this.listX() + 162, this.listY(), ROWS * ROW_H, offers.size(), 1, ROWS, this.scroll);
    }

    private void drawBottom(GuiGraphics g, VillageChamberBlockEntity be, int mouseX, int mouseY) {
        int by = this.bottomY();
        g.fill(this.leftPos + 94, by - 3, this.leftPos + this.imageWidth - 6, by - 2, SccGui.BORDER);
        String where = switch (be.output()) {
            case NETWORK -> "network";
            case NEARBY -> "nearby, then network";
            case PLAYER -> "to me";
        };
        this.button(g, this.listX(), by + 4, 158, "Results go to: " + where, mouseX, mouseY);
        SccGui.clipped(g, this.font, be.stateText(), this.listX(), by + 26, 160, SccGui.MUTED);
    }

    private void button(GuiGraphics g, int x, int y, int w, String label, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 16;
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
        if (mx >= x && mx < x + 16 && my >= y && my < y + 16) {
            g.renderTooltip(this.font, stack, mx, my);
        }
    }
}
