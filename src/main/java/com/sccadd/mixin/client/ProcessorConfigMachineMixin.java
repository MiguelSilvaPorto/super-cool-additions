package com.sccadd.mixin.client;

import com.sccadd.client.MachinePickerScreen;
import com.sccadd.machine.MachineForce;
import de.scc.calculator.client.screen.ProcessorConfigScreen;
import de.scc.calculator.network.ProcessingCalculatorBlockEntity;
import de.scc.calculator.network.ProcessorConfigMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Right-click the machine icon of a Processing Calculator to choose which machine it acts as. */
@Mixin(value = ProcessorConfigScreen.class, remap = false)
public abstract class ProcessorConfigMachineMixin {
    @Shadow
    private int tab;

    @Shadow
    protected abstract boolean overMachine(double x, double y);

    @Inject(method = "sccMouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void sccadd$pickMachine(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 1 || this.tab != 0 || !this.overMachine(mx, my)) {
            return;
        }
        ProcessorConfigMenu menu = ((AbstractContainerScreen<ProcessorConfigMenu>) (Object) this).getMenu();
        String current = "";
        if (Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(menu.pos()) instanceof ProcessingCalculatorBlockEntity be
                && ((MachineForce) be).sccadd$forced() != null) {
            current = ((MachineForce) be).sccadd$forced().toString();
        }
        Minecraft.getInstance().setScreen(new MachinePickerScreen((Screen) (Object) this, menu.pos(), current));
        cir.setReturnValue(true);
    }

    @ModifyArg(method = "sccRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"),
            index = 1, require = 0)
    private List<Component> sccadd$hint(List<Component> lines) {
        if (lines.size() == 2 && lines.get(1).getContents() instanceof TranslatableContents contents
                && "scc.gui.sides.machine.recipes".equals(contents.getKey())) {
            List<Component> more = new ArrayList<>(lines);
            more.add(Component.literal("Right-click: choose which machine this is").withStyle(ChatFormatting.YELLOW));
            return more;
        }
        return lines;
    }
}
