package com.sccadd.client;

import de.scc.calculator.client.screen.TerminalList;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Editor for {@link AdvancedFilter}: every change is saved and applied to the terminal list at once. */
public final class AdvancedFilterScreen extends Screen {
    @Nullable
    private final Screen parent;
    private final AdvancedFilter filter = AdvancedFilter.get();

    public AdvancedFilterScreen(@Nullable Screen parent) {
        super(Component.literal("Advanced filter"));
        this.parent = parent;
    }

    private void changed() {
        this.filter.save();
        TerminalList.invalidate();
    }

    private Button toggle(int x, int y, int w, String label, boolean value, Consumer<Boolean> set) {
        return Button.builder(Component.literal(label + ": " + (value ? "On" : "Off")), button -> {
            set.accept(!value);
            this.changed();
            this.init();
        }).bounds(x, y, w, 20).build();
    }

    private EditBox box(int x, int y, int w, String hint, String value, Consumer<String> set) {
        EditBox box = new EditBox(this.font, x, y, w, 18, Component.literal(hint));
        box.setMaxLength(120);
        box.setHint(Component.literal(hint));
        box.setValue(value);
        box.setResponder(text -> {
            set.accept(text);
            this.changed();
        });
        return box;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int left = this.width / 2 - 150;
        int y = 34;
        this.addRenderableWidget(Button.builder(Component.literal("Show: " + this.filter.stock.label), button -> {
            this.filter.stock = this.filter.stock.next();
            this.changed();
            this.init();
        }).bounds(left, y, 300, 20).build());
        y += 26;
        this.addRenderableWidget(this.toggle(left, y, 148, "Hide enchanted", this.filter.hideEnchanted, v -> this.filter.hideEnchanted = v));
        this.addRenderableWidget(this.toggle(left + 152, y, 148, "Hide ench. books", this.filter.hideBooks, v -> this.filter.hideBooks = v));
        y += 22;
        this.addRenderableWidget(this.toggle(left, y, 148, "Hide attributes", this.filter.hideAttributes, v -> this.filter.hideAttributes = v));
        this.addRenderableWidget(this.toggle(left + 152, y, 148, "Hide named", this.filter.hideNamed, v -> this.filter.hideNamed = v));
        y += 22;
        this.addRenderableWidget(this.toggle(left, y, 148, "Hide lore", this.filter.hideLore, v -> this.filter.hideLore = v));
        this.addRenderableWidget(this.toggle(left + 152, y, 148, "Hide damaged", this.filter.hideDamaged, v -> this.filter.hideDamaged = v));
        y += 22;
        this.addRenderableWidget(this.toggle(left, y, 300, "Plain items only (no extra data at all)", this.filter.plainOnly, v -> this.filter.plainOnly = v));
        y += 34;
        this.addRenderableWidget(this.box(left, y, 300, "Only these mods (e.g. minecraft, mekanism)", this.filter.includeMods, v -> this.filter.includeMods = v));
        y += 34;
        this.addRenderableWidget(this.box(left, y, 300, "Hide these mods", this.filter.excludeMods, v -> this.filter.excludeMods = v));
        y += 34;
        this.addRenderableWidget(this.box(left, y, 300, "Name contains (all words)", this.filter.nameHas, v -> this.filter.nameHas = v));
        y += 34;
        this.addRenderableWidget(this.box(left, y, 300, "Name does not contain", this.filter.nameHasNot, v -> this.filter.nameHasNot = v));
        y += 34;
        this.addRenderableWidget(this.box(left, y, 140, "Min. stock (in-stock items)",
                this.filter.minCount > 0 ? Long.toString(this.filter.minCount) : "", text -> {
                    try {
                        this.filter.minCount = text.isBlank() ? 0 : Math.max(0, Long.parseLong(text.trim()));
                    } catch (NumberFormatException ignored) {
                        // keep the old value while typing
                    }
                }));
        this.addRenderableWidget(Button.builder(Component.literal("Reset all"), button -> {
            this.filter.reset();
            this.changed();
            this.init();
        }).bounds(this.width / 2 - 104, this.height - 28, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(this.width / 2 + 4, this.height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("Applies to the SCC terminal list as you type."),
                this.width / 2, 22, 0xAAAAAA);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
