package com.sccadd.ae2.client;

import appeng.core.definitions.AEItems;
import appeng.recipes.handlers.ChargerRecipe;
import appeng.recipes.handlers.InscriberRecipe;
import de.scc.calculator.SccLog;
import de.scc.calculator.calc.RecipeBook;
import de.scc.calculator.calc.SccRecipe;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.client.screen.CraftSim;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * SCC reads its recipes from the recipe viewer, which does not hand over AE2's machine recipes: the
 * Inscriber and the Charger never made it into the catalogue, so no AE2 processor, press-made part
 * or charged crystal was ever "craftable" however well the calculator in front of the machine was
 * set up. This adds them: one SCC recipe per Inscriber / Charger recipe, with the machine as the
 * catalyst of its category, so a Processing Calculator in front of the machine takes the jobs.
 */
public final class Ae2MachineRecipes {
    private static final ResourceLocation INSCRIBER_TYPE = ResourceLocation.fromNamespaceAndPath("ae2", "inscriber");
    private static final ResourceLocation CHARGER_TYPE = ResourceLocation.fromNamespaceAndPath("ae2", "charger");
    private static final String INSCRIBER = "ae2:inscriber";
    private static final String CHARGER = "ae2:charger";

    private static RecipeBook done;
    private static int doneRecipes = -1;
    private static long lastCheck;

    private Ae2MachineRecipes() {
    }

    /** Cheap to call every tick: does its work once per recipe book and once the world's recipes are there. */
    public static void ensure(RecipeBook book) {
        long now = System.nanoTime();
        if (done == book && now - lastCheck < 2_000_000_000L) {
            return;
        }
        lastCheck = now;
        if (book == null || book.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        int known = minecraft.level.getRecipeManager().getRecipes().size();
        if (done == book && doneRecipes == known) {
            return;
        }
        done = book;
        doneRecipes = known;
        try {
            int added = add(book, minecraft);
            if (added > 0) {
                book.addCatalyst(INSCRIBER_TYPE.toString(), INSCRIBER);
                book.addCatalyst(CHARGER_TYPE.toString(), CHARGER);
                book.sort();
                CraftSim.invalidate();
                SccLog.info("sccadd", "added " + added + " AE2 Inscriber/Charger recipe(s) to SCC's recipe book");
            }
        } catch (RuntimeException | LinkageError failure) {
            SccLog.warn("sccadd", "could not add AE2 machine recipes: " + failure);
        }
    }

    private static int add(RecipeBook book, Minecraft minecraft) {
        int added = 0;
        var manager = minecraft.level.getRecipeManager();
        var registries = minecraft.level.registryAccess();
        for (RecipeHolder<InscriberRecipe> holder : manager.getAllRecipesFor(InscriberRecipe.TYPE)) {
            InscriberRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(registries);
            if (result.isEmpty()) {
                continue;
            }
            if (isPress(result)) {
                // "iron block + press -> press" copies a press you already have. As a recipe it reads
                // like "iron block makes a press" (the catalyst comes back), which sends iron into an
                // Inscriber with no press and never finishes. Presses are not made by SCC.
                continue;
            }
            String id = "sccadd:ae2inscriber/" + holder.id();
            if (book.byId(id) != null) {
                continue;
            }
            List<SccRecipe.Input> inputs = new ArrayList<>(3);
            addInput(inputs, recipe.getTopOptional(), true);
            addInput(inputs, recipe.getMiddleInput(), false);
            addInput(inputs, recipe.getBottomOptional(), true);
            if (inputs.isEmpty()) {
                continue;
            }
            book.add(new SccRecipe(id, INSCRIBER_TYPE, "Inscriber", StackKey.of(result), Math.max(1, result.getCount()), List.copyOf(inputs)));
            added++;
        }
        for (RecipeHolder<ChargerRecipe> holder : manager.getAllRecipesFor(ChargerRecipe.TYPE)) {
            ChargerRecipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(registries);
            if (result.isEmpty()) {
                continue;
            }
            String id = "sccadd:ae2charger/" + holder.id();
            if (book.byId(id) != null) {
                continue;
            }
            List<SccRecipe.Input> inputs = new ArrayList<>(1);
            addInput(inputs, recipe.getIngredient(), false);
            if (inputs.isEmpty()) {
                continue;
            }
            book.add(new SccRecipe(id, CHARGER_TYPE, "Charger", StackKey.of(result), Math.max(1, result.getCount()), List.copyOf(inputs)));
            added++;
        }
        return added;
    }

    /** The presses in the top and bottom slots stay in the machine; everything else is used up. */
    private static void addInput(List<SccRecipe.Input> inputs, Ingredient ingredient, boolean pressSlot) {
        if (ingredient == null || ingredient.isEmpty()) {
            return;
        }
        List<StackKey> options = new ArrayList<>();
        boolean press = pressSlot;
        for (ItemStack stack : ingredient.getItems()) {
            if (stack.isEmpty()) {
                continue;
            }
            options.add(StackKey.of(stack));
            press &= isPress(stack);
        }
        if (!options.isEmpty()) {
            inputs.add(new SccRecipe.Input(List.copyOf(options), 1, !press));
        }
    }

    private static final Set<net.minecraft.world.item.Item> PRESSES = new HashSet<>();

    private static boolean isPress(ItemStack stack) {
        if (PRESSES.isEmpty()) {
            PRESSES.add(AEItems.LOGIC_PROCESSOR_PRESS.asItem());
            PRESSES.add(AEItems.CALCULATION_PROCESSOR_PRESS.asItem());
            PRESSES.add(AEItems.ENGINEERING_PROCESSOR_PRESS.asItem());
            PRESSES.add(AEItems.SILICON_PRESS.asItem());
        }
        return PRESSES.contains(stack.getItem());
    }
}
