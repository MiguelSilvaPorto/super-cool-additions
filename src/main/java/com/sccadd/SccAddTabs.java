package com.sccadd;

import com.sccadd.iface.CompactInterface;
import com.sccadd.village.VillageRegistry;
import de.scc.calculator.network.BreakingGrade;
import de.scc.calculator.network.SccRegistry;
import de.scc.calculator.network.SccTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The addon's own creative tab: what sccadd adds, followed by the SCC blocks each of its features
 * touches, so the whole set is one click away in the creative inventory. It sits right after SCC's
 * own tab.
 */
public final class SccAddTabs {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SccAdditions.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ADDITIONS = TABS.register("additions",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("sccadd.tab"))
                    .icon(() -> new ItemStack(SccRegistry.BREAKING_CALCULATORS.get(BreakingGrade.BASIC).get()))
                    .displayItems(SccAddTabs::fill)
                    .withTabsBefore(ResourceLocation.fromNamespaceAndPath("scc", "main"))
                    .build());

    private SccAddTabs() {
    }

    public static void init(IEventBus modBus) {
        TABS.register(modBus);
    }

    /** The addon's own items first, then the SCC machines behind each feature it works on. */
    private static void fill(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output out) {
        // What the addon itself adds.
        out.accept(CompactInterface.ITEM.get());
        if (ModList.get().isLoaded("easy_villagers")) {
            for (var item : VillageRegistry.CHAMBER_ITEMS.values()) {
                if (item.isBound()) {
                    out.accept(item.get());
                }
            }
        }
        // Breaking Calculator: the performance work (budget, batch mode, magnet reach) covers every grade.
        for (BreakingGrade grade : BreakingGrade.values()) {
            out.accept(SccRegistry.BREAKING_CALCULATORS.get(grade).get());
        }
        // Processing: one calculator that can act as any machine (Basic of each family, like SCC's own tab).
        out.accept(SccRegistry.PROCESSING_CALCULATORS.get(SccTier.BASIC).get());
        out.accept(SccRegistry.ADVANCED_PROCESSING_CALCULATORS.get(SccTier.BASIC).get());
        out.accept(SccRegistry.CUSTOM_PROCESSING_CALCULATORS.get(SccTier.BASIC).get());
        // AE2 bridge: stock and patterns both ways, the wireless pair, and the Interface fluid tanks.
        out.accept(SccRegistry.STORAGE_CONNECTOR.get());
        out.accept(SccRegistry.NETWORK_ANTENNA.get());
        out.accept(SccRegistry.WIRELESS_TRANSMITTER.get());
        out.accept(SccRegistry.WIRELESS_RECEIVER.get());
        out.accept(SccRegistry.INTERFACE.get());
        // Terminals: filter variants, the drag-to-edit bar and the pocket panel live here.
        out.accept(SccRegistry.TERMINAL.get());
        out.accept(SccRegistry.CRAFTING_TERMINAL.get());
        out.accept(SccRegistry.WIRELESS_TERMINAL.get());
        out.accept(SccRegistry.WIRELESS_CRAFTING_TERMINAL.get());
        // Requester and the jobs the Think button plans ahead.
        out.accept(SccRegistry.REQUESTER.get());
        out.accept(SccRegistry.CALCULATION_CRAFTERS.get(SccTier.BASIC).get());
    }
}
