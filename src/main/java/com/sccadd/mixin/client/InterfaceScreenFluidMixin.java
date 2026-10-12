package com.sccadd.mixin.client;

import com.sccadd.iface.InterfaceTanks;
import de.scc.calculator.client.screen.InterfaceScreen;
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

    @Inject(method = "sccRender", at = @At("TAIL"), require = 0)
    private void sccadd$drawFluids(GuiGraphics gfx, int mx, int my, float pt, CallbackInfo ci) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        if (Minecraft.getInstance().level == null || !(screen.getMenu() instanceof InterfaceMenu menu)
                || !(Minecraft.getInstance().level.getBlockEntity(menu.pos()) instanceof InterfaceBlockEntity be)) {
            return;
        }
        int left = ((ContainerScreenAccess) this).sccadd$left();
        int top = ((ContainerScreenAccess) this).sccadd$top();
        int hover = this.templateAt(mx, my);
        for (int i = 0; i < InterfaceBlockEntity.SLOTS; i++) {
            Fluid fluid = InterfaceTanks.templateOf(be.filter().entry(i));
            if (fluid == null) {
                continue;
            }
            int x = left + InterfaceMenu.SLOT_X + i * 18;
            int y = top + InterfaceMenu.TEMPLATE_Y;
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
            ResourceLocation still = ext.getStillTexture();
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);
            int tint = ext.getTintColor();
            gfx.blit(x + 1, y + 1, 0, 16, 16, sprite,
                    (tint >> 16 & 255) / 255f, (tint >> 8 & 255) / 255f, (tint & 255) / 255f, 1f);
            if (hover == i) {
                gfx.renderComponentTooltip(Minecraft.getInstance().font, java.util.List.of(
                        fluid.getFluidType().getDescription(),
                        Component.literal("Fluid template: stocked from the network into a "
                                + InterfaceTanks.CAPACITY / 1000 + " bucket tank."),
                        Component.literal("Right-click with an empty cursor to clear.")), mx, my);
            }
        }
    }
}
