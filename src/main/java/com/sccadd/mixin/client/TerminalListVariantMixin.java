package com.sccadd.mixin.client;

import com.sccadd.SccAddConfig;
import de.scc.calculator.network.TerminalBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With "Clean variants" on, craftable-only entries that carry enchantments,
 * attribute changes, a custom name or lore are dropped from the terminal list.
 * Anything the network really holds stays visible.
 */
@Mixin(targets = "de.scc.calculator.client.screen.TerminalList", remap = false)
public abstract class TerminalListVariantMixin {
    private static final Set<DataComponentType<?>> NOISE = Set.of(
            DataComponents.ENCHANTMENTS,
            DataComponents.STORED_ENCHANTMENTS,
            DataComponents.ATTRIBUTE_MODIFIERS,
            DataComponents.CUSTOM_NAME,
            DataComponents.LORE,
            DataComponents.RARITY,
            DataComponents.REPAIR_COST,
            DataComponents.UNBREAKABLE,
            DataComponents.ENCHANTMENT_GLINT_OVERRIDE);

    @Inject(method = "build(Lde/scc/calculator/network/TerminalBlockEntity;Ljava/lang/String;Lde/scc/calculator/client/screen/SortMode;ZZ[I)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private static void sccadd$cleanVariants(TerminalBlockEntity be, String query, de.scc.calculator.client.screen.SortMode sort,
                                             boolean showCraftable, boolean onlyAvailable, int[] simBudget,
                                             CallbackInfoReturnable<List<TerminalBlockEntity.Entry>> cir) {
        if (!SccAddConfig.cleanVariants()) {
            return;
        }
        List<TerminalBlockEntity.Entry> in = cir.getReturnValue();
        if (in == null || in.isEmpty()) {
            return;
        }
        List<TerminalBlockEntity.Entry> out = new ArrayList<>(in.size());
        for (TerminalBlockEntity.Entry entry : in) {
            if (entry.count() > 0L || entry.fluid() != null || entry.isChemical() || entry.isSoul() || entry.isSource()
                    || !noisy(entry)) {
                out.add(entry);
            }
        }
        cir.setReturnValue(out);
    }

    private static boolean noisy(TerminalBlockEntity.Entry entry) {
        try {
            ItemStack stack = entry.item().display();
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            // The patch holds only what differs from the item's defaults.
            for (java.util.Map.Entry<DataComponentType<?>, java.util.Optional<?>> change : stack.getComponentsPatch().entrySet()) {
                if (change.getValue().isPresent() && NOISE.contains(change.getKey())) {
                    return true;
                }
            }
        } catch (Throwable failed) {
            // keep the entry when unsure
        }
        return false;
    }
}
