package com.sccadd.processing;

import com.sccadd.SccAddConfig;
import de.scc.calculator.network.ProcessingCalculatorBlockEntity;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Items that a machine keeps and reuses (the AE2 Inscriber's presses): they are not used up by a
 * recipe, so a press already inside the machine is all the recipe needs.
 */
public final class Catalysts {
    private static final Map<ProcessingCalculatorBlockEntity, MachineCatalystConnector> CONNECTORS = new WeakHashMap<>();
    private static Set<Item> items = Set.of();
    private static List<? extends String> source;

    /** How long a machine with only presses in it must sit idle before they go back to the system. */
    public static final int RELEASE_AFTER_TICKS = 100;
    private static final Map<ProcessingCalculatorBlockEntity, Integer> IDLE = new WeakHashMap<>();

    private Catalysts() {
    }

    public static synchronized boolean is(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        List<? extends String> configured = SccAddConfig.reusableCatalysts();
        if (configured != source) {
            Set<Item> found = new HashSet<>();
            for (String id : configured) {
                ResourceLocation key = ResourceLocation.tryParse(id.trim());
                if (key != null) {
                    BuiltInRegistries.ITEM.getOptional(key).ifPresent(found::add);
                }
            }
            items = found;
            source = configured;
        }
        return items.contains(stack.getItem());
    }

    public static synchronized MachineCatalystConnector connector(ProcessingCalculatorBlockEntity calculator) {
        return CONNECTORS.computeIfAbsent(calculator, MachineCatalystConnector::new);
    }

    /** Counts the server ticks this machine has been idle (resets when it is not); returns the count. */
    public static synchronized int idle(ProcessingCalculatorBlockEntity machine, boolean idle) {
        if (!idle) {
            IDLE.remove(machine);
            return 0;
        }
        return IDLE.merge(machine, 1, Integer::sum);
    }
}
