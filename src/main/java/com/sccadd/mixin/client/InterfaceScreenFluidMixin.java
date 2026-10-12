package com.sccadd.mixin.client;

import com.sccadd.iface.InterfaceTanks;
import de.scc.calculator.client.screen.InterfaceScreen;
import de.scc.calculator.client.screen.ViewerAreas;
import de.scc.calculator.network.InterfaceBlockEntity;
import de.scc.calculator.network.InterfaceMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Clicking a template cell with a bucket (or any fluid container) on the cursor makes it a fluid
 * template: the Interface keeps that fluid stocked from the network in the cell's tank.
 */
@Mixin(value = InterfaceScreen.class, remap = false)
public abstract class InterfaceScreenFluidMixin {
    @Shadow
    protected abstract int templateAt(double x, double y);

    @Shadow
    protected abstract void setEntry(int slot, String entry);

    @Inject(method = "sccMouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$fluidTemplate(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        int cell = this.templateAt(mx, my);
        if (cell < 0 || (button != 0 && button != 1)) {
            return;
        }
        ItemStack carried = ((AbstractContainerScreen<?>) (Object) this).getMenu().getCarried();
        if (carried.isEmpty()) {
            return;
        }
        FluidStack fluid = FluidUtil.getFluidContained(carried).orElse(FluidStack.EMPTY);
        if (fluid.isEmpty()) {
            return;
        }
        this.setEntry(cell, InterfaceTanks.entryOf(fluid.getFluid()));
        cir.setReturnValue(true);
    }

    /** Dragging a fluid out of the recipe viewer onto a template cell sets it, no bucket needed. */
    @Inject(method = "sccGhostSlots", at = @At("RETURN"), cancellable = true, require = 0)
    private void sccadd$fluidGhosts(CallbackInfoReturnable<java.util.List<ViewerAreas.GhostSlot>> cir) {
        java.util.List<ViewerAreas.GhostSlot> base = cir.getReturnValue();
        if (base == null) {
            return;
        }
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        int left = ((ContainerScreenAccess) this).sccadd$left();
        int top = ((ContainerScreenAccess) this).sccadd$top();
        java.util.List<ViewerAreas.GhostSlot> out = new java.util.ArrayList<>(base.size());
        for (ViewerAreas.GhostSlot slot : base) {
            net.minecraft.client.renderer.Rect2i area = slot.area();
            int cell = Math.floorDiv(area.getX() - (left + InterfaceMenu.SLOT_X) + 2, 18);
            boolean template = Math.abs(area.getY() - (top + InterfaceMenu.TEMPLATE_Y)) <= 2 && cell >= 0 && cell < InterfaceBlockEntity.SLOTS;
            if (template && slot.acceptFluid() == null) {
                out.add(new ViewerAreas.GhostSlot(area, slot.accept(), (FluidStack fluid) -> {
                    if (!fluid.isEmpty()) {
                        this.setEntry(cell, InterfaceTanks.entryOf(fluid.getFluid()));
                    }
                }));
            } else {
                out.add(slot);
            }
        }
        cir.setReturnValue(out);
    }

    /** Which fluid cell the mouse is over, in the template row (0) or the stock row (1), or -1. */
    private int sccadd$fluidCell(double mx, double my, int[] row) {
        int left = ((ContainerScreenAccess) this).sccadd$left();
        int top = ((ContainerScreenAccess) this).sccadd$top();
        int cell = Math.floorDiv((int) mx - (left + InterfaceMenu.SLOT_X), 18);
        if (cell < 0 || cell >= InterfaceBlockEntity.SLOTS) {
            return -1;
        }
        if (my >= top + InterfaceMenu.TEMPLATE_Y && my < top + InterfaceMenu.TEMPLATE_Y + 18) {
            row[0] = 0;
            return cell;
        }
        if (my >= top + InterfaceMenu.BUFFER_Y && my < top + InterfaceMenu.BUFFER_Y + 18) {
            row[0] = 1;
            return cell;
        }
        return -1;
    }

    private static void sccadd$tank(GuiGraphics gfx, Fluid fluid, int x, int y, int fill) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture());
        int tint = ext.getTintColor();
        gfx.fill(x + 1, y + 1, x + 17, y + 17, 0xFF0B1116);
        if (fill > 0) {
            gfx.enableScissor(x + 1, y + 17 - fill, x + 17, y + 17);
            gfx.blit(x + 1, y + 1, 0, 16, 16, sprite,
                    (tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f, (tint & 255) / 255f, 1f);
            gfx.disableScissor();
        }
        gfx.renderOutline(x, y, 18, 18, 0xFF4FD1BE);
    }

    /** The template row shows the fluid; the stock row below shows the tank filling, with its amount. */
    @Inject(method = "sccRender", at = @At("TAIL"), require = 0)
    private void sccadd$drawFluids(GuiGraphics gfx, int mx, int my, float pt, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (Minecraft.getInstance().level == null || !(screen.getMenu() instanceof InterfaceMenu menu)
                || !(Minecraft.getInstance().level.getBlockEntity(menu.pos()) instanceof InterfaceBlockEntity be)) {
            return;
        }
        int left = ((ContainerScreenAccess) this).sccadd$left();
        int top = ((ContainerScreenAccess) this).sccadd$top();
        var font = Minecraft.getInstance().font;
        InterfaceTanks tanks = ((com.sccadd.iface.TankHolder) be).sccadd$tanks();
        int[] row = new int[1];
        int hover = this.sccadd$fluidCell(mx, my, row);
        Fluid hovered = null;
        int hoveredAmount = 0;
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 300);
        for (int i = 0; i < InterfaceBlockEntity.SLOTS; i++) {
            Fluid fluid = InterfaceTanks.templateOf(be.filter().entry(i));
            if (fluid == null) {
                continue;
            }
            int x = left + InterfaceMenu.SLOT_X + i * 18;
            int amount = tanks.stocked(i);
            sccadd$tank(gfx, fluid, x, top + InterfaceMenu.TEMPLATE_Y, 16);
            int y = top + InterfaceMenu.BUFFER_Y;
            sccadd$tank(gfx, fluid, x, y, amount <= 0 ? 0 : Math.max(1, Math.min(16, amount * 16 / InterfaceTanks.CAPACITY)));
            String text = amount >= 1000 ? (amount / 1000) + "B" : amount + "";
            gfx.pose().pushPose();
            gfx.pose().translate(x + 17 - font.width(text) * 0.5f, y + 11, 50);
            gfx.pose().scale(0.5f, 0.5f, 1f);
            gfx.drawString(font, text, 0, 0, 0xFFFFFFFF, true);
            gfx.pose().popPose();
            if (hover == i) {
                hovered = fluid;
                hoveredAmount = amount;
            }
        }
        gfx.pose().popPose();
        if (hovered != null) {
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 400);
            gfx.renderComponentTooltip(font, java.util.List.of(
                    hovered.getFluidType().getDescription(),
                    Component.literal("Fluid tank: " + hoveredAmount + " / " + InterfaceTanks.CAPACITY + " mB"),
                    Component.literal("Stocked from the network; pipes and tanks beside it take from it."),
                    Component.literal("Fluid poured in from outside goes to the network."),
                    Component.literal("Right-click the template with an empty cursor to clear.")), mx, my);
            gfx.pose().popPose();
        }
    }

    /** SCC's own tooltip for a template cell would sit over ours: leave it out when the cell holds a fluid. */
    @org.spongepowered.asm.mixin.injection.Redirect(method = "sccRender", at = @At(value = "INVOKE",
            target = "Lde/scc/calculator/client/screen/SccGui;tooltip(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"),
            require = 0)
    private void sccadd$ownTooltip(GuiGraphics gfx, net.minecraft.client.gui.Font font, java.util.List<Component> lines, int x, int y) {
        int[] row = new int[1];
        int cell = this.sccadd$fluidCell(x, y, row);
        if (cell >= 0 && Minecraft.getInstance().level != null
                && ((AbstractContainerScreen<?>) (Object) this).getMenu() instanceof InterfaceMenu menu
                && Minecraft.getInstance().level.getBlockEntity(menu.pos()) instanceof InterfaceBlockEntity be
                && InterfaceTanks.templateOf(be.filter().entry(cell)) != null) {
            return;
        }
        de.scc.calculator.client.screen.SccGui.tooltip(gfx, font, lines, x, y);
    }
}
