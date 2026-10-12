package com.sccadd.client.chain;

import de.scc.calculator.calc.SccRecipe;
import de.scc.calculator.calc.StackKey;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * SCC's own recipe picker (the one the crafting tree opens with a right-click), hosted here as it
 * is: same cards, same machines, same colours. It is package-private in SCC, so it is driven
 * through reflection; if SCC changes it, {@link #available()} turns false and the editor falls back.
 */
public final class RecipePickerHost {
    public static final int WIDTH = 320;
    public static final int HEIGHT = 218;

    private static final Constructor<?> CTOR;
    private static final Method DRAW;
    private static final Method TOOLTIP;
    private static final Method CLICK;
    private static final Method SCROLL;
    private static final Method CONTAINS;

    static {
        Constructor<?> ctor = null;
        Method draw = null;
        Method tooltip = null;
        Method click = null;
        Method scroll = null;
        Method contains = null;
        try {
            Class<?> type = Class.forName("de.scc.calculator.client.screen.RecipePicker");
            ctor = type.getDeclaredConstructor(StackKey.class, long.class, List.class, String.class,
                    java.util.function.Supplier.class, int.class, int.class, int.class, int.class, Consumer.class, Runnable.class);
            ctor.setAccessible(true);
            draw = type.getDeclaredMethod("draw", GuiGraphics.class, Font.class, int.class, int.class);
            tooltip = type.getDeclaredMethod("tooltip", GuiGraphics.class, Font.class, int.class, int.class);
            click = type.getDeclaredMethod("click", double.class, double.class, Font.class);
            scroll = type.getDeclaredMethod("scroll", double.class);
            contains = type.getDeclaredMethod("contains", double.class, double.class);
            for (Method method : new Method[] {draw, tooltip, click, scroll, contains}) {
                method.setAccessible(true);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            ctor = null;
        }
        CTOR = ctor;
        DRAW = draw;
        TOOLTIP = tooltip;
        CLICK = click;
        SCROLL = scroll;
        CONTAINS = contains;
    }

    private final Object picker;

    private RecipePickerHost(Object picker) {
        this.picker = picker;
    }

    public static boolean available() {
        return CTOR != null;
    }

    /** Opens SCC's picker for the item's recipes; {@code onPick} gets the chosen recipe. */
    public static RecipePickerHost open(StackKey item, SccRecipe current, int screenWidth, int screenHeight,
            Consumer<SccRecipe> onPick, Runnable onClose) {
        if (CTOR == null) {
            return null;
        }
        List<SccRecipe> recipes = ChainSteps.recipesFor(item);
        if (recipes.isEmpty()) {
            return null;
        }
        Consumer<String> byId = id -> {
            for (SccRecipe recipe : recipes) {
                if (recipe.id().equals(id)) {
                    onPick.accept(recipe);
                    return;
                }
            }
        };
        try {
            Object picker = CTOR.newInstance(item, 1L, recipes, current == null ? "" : current.id(),
                    (java.util.function.Supplier<Map<StackKey, Long>>) Map::of,
                    (screenWidth - WIDTH) / 2, Math.max(8, (screenHeight - HEIGHT) / 2), WIDTH, HEIGHT, byId, onClose);
            return new RecipePickerHost(picker);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    public void draw(GuiGraphics g, Font font, int mx, int my) {
        try {
            DRAW.invoke(this.picker, g, font, mx, my);
            TOOLTIP.invoke(this.picker, g, font, mx, my);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // drawn nothing this frame
        }
    }

    public boolean click(double mx, double my, Font font) {
        try {
            return (boolean) CLICK.invoke(this.picker, mx, my, font);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    public boolean contains(double mx, double my) {
        try {
            return (boolean) CONTAINS.invoke(this.picker, mx, my);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return false;
        }
    }

    public void scroll(double delta) {
        try {
            SCROLL.invoke(this.picker, delta);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // nothing to scroll
        }
    }
}
