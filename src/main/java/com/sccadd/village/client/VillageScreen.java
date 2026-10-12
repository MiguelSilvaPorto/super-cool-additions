package com.sccadd.village.client;

import com.sccadd.village.VillageConfigPayload;
import com.sccadd.village.VillageMenu;
import com.sccadd.village.VillageSimulatorBlockEntity;
import de.scc.calculator.client.screen.SccContainerScreen;
import de.scc.calculator.client.screen.SccGui;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Auto Trader's window, in SCC's own style: the villager's offers with a box to trade each one,
 * how many of the result the network should keep, where the results go, and whether to level up.
 */
public final class VillageScreen extends SccContainerScreen<VillageMenu> {
    private static final int ROW_H = 22;
    private static final int ROWS = 7;

    private int scroll;
    /** The row whose "keep" number is being typed, or null. */
    @Nullable
    private String editing;
    private String typed = "";

    public VillageScreen(VillageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 236;
        this.imageHeight = 26 + ROWS * ROW_H + 52;
    }

    @Override
    protected boolean hasRail() {
        return false;
    }

    @Nullable
    private VillageSimulatorBlockEntity be() {
        return Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(this.menu.pos()) instanceof VillageSimulatorBlockEntity be ? be : null;
    }

    private void send(int action, String signature, int value) {
        PacketDistributor.sendToServer(new VillageConfigPayload(this.menu.pos(), action, signature, value));
    }

    private int listX() {
        return this.leftPos + 8;
    }

    private int listY() {
        return this.topPos + 34;
    }

    private int rowY(int row) {
        return this.listY() + row * ROW_H;
    }

    private int bottomY() {
        return this.listY() + ROWS * ROW_H + 6;
    }

    // ---- input ----

    @Override
    protected boolean sccMouseClicked(double mx, double my, int button) {
        VillageSimulatorBlockEntity be = this.be();
        if (be == null) {
            return false;
        }
        if (button == 0) {
            MerchantOffers offers = be.offers();
            for (int row = 0; row < ROWS; row++) {
                int index = this.scroll + row;
                if (index >= offers.size()) {
                    break;
                }
                MerchantOffer offer = offers.get(index);
                String sig = VillageSimulatorBlockEntity.signature(offer);
                int y = this.rowY(row);
                if (my >= y && my < y + ROW_H - 2) {
                    boolean on = be.picked().containsKey(sig);
                    if (mx >= this.listX() && mx < this.listX() + 16) {
                        this.commitTyping();
                        this.send(VillageConfigPayload.PICK, sig, on ? -1 : 0);
                        return true;
                    }
                    if (on && mx >= this.listX() + 168 && mx < this.listX() + 224) {
                        this.commitTyping();
                        this.editing = sig;
                        this.typed = Integer.toString(be.picked().getOrDefault(sig, 0));
                        return true;
                    }
                }
            }
            int by = this.bottomY();
            if (my >= by + 16 && my < by + 32) {
                this.commitTyping();
                if (mx >= this.listX() && mx < this.listX() + 108) {
                    this.send(VillageConfigPayload.OUTPUT, "", be.output() == VillageSimulatorBlockEntity.Output.NETWORK ? 1 : 0);
                    return true;
                }
                if (mx >= this.listX() + 112 && mx < this.listX() + 220) {
                    this.send(VillageConfigPayload.LEVEL_UP, "", be.levelUp() ? 0 : 1);
                    return true;
                }
            }
        }
        this.commitTyping();
        return super.sccMouseClicked(mx, my, button);
    }

    private void commitTyping() {
        VillageSimulatorBlockEntity be = this.be();
        if (this.editing != null && be != null && be.picked().containsKey(this.editing)) {
            int keep = 0;
            try {
                keep = Math.max(0, Math.min(1_000_000, Integer.parseInt(this.typed.isBlank() ? "0" : this.typed)));
            } catch (NumberFormatException ignored) {
                // keep 0
            }
            this.send(VillageConfigPayload.PICK, this.editing, keep);
        }
        this.editing = null;
    }

    @Override
    protected boolean sccMouseScrolled(double mx, double my, double dx, double dy) {
        VillageSimulatorBlockEntity be = this.be();
        int size = be == null ? 0 : be.offers().size();
        this.scroll = Math.max(0, Math.min(Math.max(0, size - ROWS), this.scroll - (int) Math.signum(dy)));
        return true;
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (this.editing != null && Character.isDigit(ch) && this.typed.length() < 7) {
            this.typed += ch;
            return true;
        }
        return super.charTyped(ch, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (this.editing != null) {
            if (key == 257 || key == 335) {
                this.commitTyping();
                return true;
            }
            if (key == 259) {
                if (!this.typed.isEmpty()) {
                    this.typed = this.typed.substring(0, this.typed.length() - 1);
                }
                return true;
            }
            if (key == 256) {
                this.editing = null;
                return true;
            }
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
        VillageSimulatorBlockEntity be = this.be();
        g.drawString(this.font, "Village Auto Trader", this.leftPos + 8, this.topPos + 4, SccGui.ACCENT, false);
        if (be == null) {
            return;
        }
        String who = be.workstationName() + ", " + be.stateText();
        SccGui.clipped(g, this.font, who, this.leftPos + 8, this.topPos + 19, this.imageWidth - 16, SccGui.MUTED);

        MerchantOffers offers = be.offers();
        if (offers.isEmpty()) {
            g.drawString(this.font, be.villagerStack().isEmpty() ? "No villager inside." : "No offers yet - add a workstation.",
                    this.listX() + 4, this.listY() + 6, SccGui.MUTED, false);
        }
        for (int row = 0; row < ROWS; row++) {
            int index = this.scroll + row;
            if (index >= offers.size()) {
                break;
            }
            MerchantOffer offer = offers.get(index);
            String sig = VillageSimulatorBlockEntity.signature(offer);
            boolean on = be.picked().containsKey(sig);
            int x = this.listX();
            int y = this.rowY(row);
            boolean hover = mouseX >= x && mouseX < x + 224 && mouseY >= y && mouseY < y + ROW_H - 2;
            g.fill(x, y, x + 224, y + ROW_H - 2, hover ? SccGui.ROW_HOVER : SccGui.PLATE_BG);
            // the box that turns trading on for this offer
            g.fill(x + 3, y + 3, x + 15, y + 15, 0xFF10161C);
            SccGui.outline(g, x + 3, y + 3, 12, 12, on ? SccGui.ACCENT : SccGui.BORDER);
            if (on) {
                g.fill(x + 6, y + 6, x + 12, y + 12, SccGui.ACCENT);
            }
            int ix = x + 22;
            this.item(g, offer.getCostA(), ix, y + 2, mouseX, mouseY);
            if (!offer.getCostB().isEmpty()) {
                g.drawString(this.font, "+", ix + 22, y + 7, SccGui.MUTED, false);
                this.item(g, offer.getCostB(), ix + 30, y + 2, mouseX, mouseY);
            }
            g.drawString(this.font, "→", x + 92, y + 7, SccGui.MUTED, false);
            this.item(g, offer.getResult(), x + 104, y + 2, mouseX, mouseY);
            if (offer.isOutOfStock()) {
                g.drawString(this.font, "sold out", x + 130, y + 7, SccGui.WARN, false);
            }
            if (on) {
                boolean editing = sig.equals(this.editing);
                int keep = be.picked().getOrDefault(sig, 0);
                String text = editing ? this.typed + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "")
                        : (keep > 0 ? "keep " + keep : "no limit");
                g.fill(x + 168, y + 3, x + 222, y + 17, 0xFF10161C);
                SccGui.outline(g, x + 168, y + 3, 54, 14, editing ? SccGui.ACCENT : SccGui.BORDER);
                g.drawString(this.font, this.font.plainSubstrByWidth(text, 50), x + 171, y + 6, SccGui.TEXT, false);
                if (hover && mouseX >= x + 168) {
                    SccGui.tooltip(g, this.font, List.of(Component.literal("Keep this many of the result in the network."),
                            Component.literal("The villager stops trading it once the network holds that many. 0 = no limit.")), mouseX, mouseY);
                }
            } else if (hover && mouseX < x + 20) {
                SccGui.tooltip(g, this.font, Component.literal("Trade this offer automatically"), mouseX, mouseY);
            }
        }
        SccGui.verticalScrollbar(g, this.listX() + 226, this.listY(), ROWS * ROW_H, offers.size(), 1, ROWS, this.scroll);

        int by = this.bottomY();
        g.fill(this.leftPos + 6, by - 3, this.leftPos + this.imageWidth - 6, by - 2, SccGui.BORDER);
        g.drawString(this.font, "Picked: " + be.picked().size() + "   Level up with emeralds first, until a picked offer is there.",
                this.listX(), by + 2, SccGui.MUTED, false);
        this.button(g, this.listX(), by + 16, 108, "Output: " + (be.output() == VillageSimulatorBlockEntity.Output.NETWORK ? "network" : "nearby"),
                mouseX, mouseY);
        this.button(g, this.listX() + 112, by + 16, 108, "Level up: " + (be.levelUp() ? "on" : "off"), mouseX, mouseY);
        int hy = by + 16;
        if (mouseY >= hy && mouseY < hy + 16 && mouseX >= this.listX() && mouseX < this.listX() + 108) {
            SccGui.tooltip(g, this.font, List.of(Component.literal("Where the trades' results go."),
                    Component.literal("network: back into the system"),
                    Component.literal("nearby: chests and machines around this block first, the rest to the system")), mouseX, mouseY);
        }
    }

    private void button(GuiGraphics g, int x, int y, int w, String label, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + 16;
        g.fill(x, y, x + w, y + 16, hover ? SccGui.ROW_HOVER : SccGui.PLATE_BG);
        SccGui.outline(g, x, y, w, 16, hover ? SccGui.ACCENT : SccGui.BORDER);
        g.drawString(this.font, label, x + 6, y + 4, SccGui.TEXT, false);
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
