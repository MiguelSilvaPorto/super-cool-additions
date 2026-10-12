package com.sccadd.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.scc.calculator.network.TerminalBlockEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

/**
 * The advanced filter of the SCC terminal list: what to show, by stock, variant
 * traits, mod and name. Saved in config/sccadd-filter.json.
 */
public final class AdvancedFilter {
    public enum Stock {
        ALL("All entries"),
        IN_STOCK("In stock only"),
        MISSING("Craftable only (not in stock)");

        public final String label;

        Stock(String label) {
            this.label = label;
        }

        public Stock next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    public Stock stock = Stock.ALL;
    public boolean hideEnchanted;
    public boolean hideBooks;
    public boolean hideAttributes;
    public boolean hideNamed;
    public boolean hideLore;
    public boolean hideDamaged;
    public boolean plainOnly;
    public long minCount;
    public String includeMods = "";
    public String excludeMods = "";
    public String nameHas = "";
    public String nameHasNot = "";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AdvancedFilter current;

    public static synchronized AdvancedFilter get() {
        if (current == null) {
            current = load();
        }
        return current;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("sccadd-filter.json");
    }

    private static AdvancedFilter load() {
        try {
            Path file = file();
            if (Files.exists(file)) {
                AdvancedFilter read = GSON.fromJson(Files.readString(file), AdvancedFilter.class);
                if (read != null) {
                    if (read.stock == null) {
                        read.stock = Stock.ALL;
                    }
                    return read;
                }
            }
        } catch (Exception failed) {
            // fall back to defaults
        }
        return new AdvancedFilter();
    }

    public synchronized void save() {
        try {
            Files.writeString(file(), GSON.toJson(this));
        } catch (IOException failed) {
            // not critical
        }
    }

    public boolean active() {
        return this.stock != Stock.ALL || this.hideEnchanted || this.hideBooks || this.hideAttributes || this.hideNamed
                || this.hideLore || this.hideDamaged || this.plainOnly || this.minCount > 0
                || !this.includeMods.isBlank() || !this.excludeMods.isBlank()
                || !this.nameHas.isBlank() || !this.nameHasNot.isBlank();
    }

    public void reset() {
        AdvancedFilter fresh = new AdvancedFilter();
        this.stock = fresh.stock;
        this.hideEnchanted = this.hideBooks = this.hideAttributes = this.hideNamed = false;
        this.hideLore = this.hideDamaged = this.plainOnly = false;
        this.minCount = 0;
        this.includeMods = this.excludeMods = this.nameHas = this.nameHasNot = "";
    }

    private static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.toLowerCase(Locale.ROOT).split("[,\\s]+")) {
            if (!part.isBlank()) {
                out.add(part);
            }
        }
        return out;
    }

    private static boolean has(ItemStack stack, DataComponentType<?> type) {
        var change = stack.getComponentsPatch().get(type);
        return change != null && change.isPresent();
    }

    /** True when the entry stays in the list. Fluids and the like only follow the stock rule. */
    public boolean accept(TerminalBlockEntity.Entry entry) {
        boolean plainItem = !entry.isFluid() && !entry.isChemical() && !entry.isSource() && !entry.isSoul();
        if (!plainItem) {
            return this.stock != Stock.MISSING || entry.count() <= 0L;
        }
        if (this.stock == Stock.IN_STOCK && entry.count() <= 0L) {
            return false;
        }
        if (this.stock == Stock.MISSING && entry.count() > 0L) {
            return false;
        }
        if (this.minCount > 0 && entry.count() > 0L && entry.count() < this.minCount) {
            return false;
        }
        ItemStack stack = entry.item().display();
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        if (this.hideEnchanted && has(stack, DataComponents.ENCHANTMENTS)) {
            return false;
        }
        if (this.hideBooks && has(stack, DataComponents.STORED_ENCHANTMENTS)) {
            return false;
        }
        if (this.hideAttributes && has(stack, DataComponents.ATTRIBUTE_MODIFIERS)) {
            return false;
        }
        if (this.hideNamed && has(stack, DataComponents.CUSTOM_NAME)) {
            return false;
        }
        if (this.hideLore && has(stack, DataComponents.LORE)) {
            return false;
        }
        if (this.hideDamaged && stack.isDamaged()) {
            return false;
        }
        if (this.plainOnly && !stack.getComponentsPatch().isEmpty()) {
            return false;
        }
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        List<String> include = words(this.includeMods);
        if (!include.isEmpty() && !include.contains(namespace)) {
            return false;
        }
        if (words(this.excludeMods).contains(namespace)) {
            return false;
        }
        if (!this.nameHas.isBlank() || !this.nameHasNot.isBlank()) {
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            for (String word : words(this.nameHas)) {
                if (!name.contains(word)) {
                    return false;
                }
            }
            for (String word : words(this.nameHasNot)) {
                if (name.contains(word)) {
                    return false;
                }
            }
        }
        return true;
    }
}
