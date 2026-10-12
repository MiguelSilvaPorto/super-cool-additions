package com.sccadd.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import de.scc.calculator.SccLog;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.FuelOrders;
import de.scc.calculator.network.NetworkBlockEntity;
import de.scc.calculator.network.NetworkView;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Turns SCC recipes into real AE2 patterns and executes them: when an AE2 CPU
 * pushes the ingredients, they go into the SCC network and SCC starts the job.
 * The finished items are handed back to AE2 by the antenna's normal delivery path.
 */
public final class SccAe2Patterns {
    private SccAe2Patterns() {
    }

    public static List<IPatternDetails> build(List<SccAe2Recipe> recipes) {
        List<IPatternDetails> out = new ArrayList<>(recipes.size());
        for (SccAe2Recipe recipe : recipes) {
            IPatternDetails pattern = buildOne(recipe);
            if (pattern != null) {
                out.add(pattern);
            }
        }
        return List.copyOf(out);
    }

    @Nullable
    private static IPatternDetails buildOne(SccAe2Recipe recipe) {
        AEItemKey outputKey = AEItemKey.of(recipe.output().display());
        if (outputKey == null) {
            return null;
        }
        List<IPatternDetails.IInput> inputs = new ArrayList<>(recipe.inputs().size() + recipe.fluids().size());
        for (SccAe2Recipe.Input input : recipe.inputs()) {
            GenericStack[] options = new GenericStack[input.options().size()];
            int used = 0;
            for (StackKey option : input.options()) {
                AEItemKey key = AEItemKey.of(option.display());
                if (key != null) {
                    options[used++] = new GenericStack(key, input.count());
                }
            }
            if (used == 0) {
                return null;
            }
            if (used < options.length) {
                options = Arrays.copyOf(options, used);
            }
            inputs.add(new SimpleInput(options, 1L));
        }
        for (SccAe2Recipe.FluidInput fluid : recipe.fluids()) {
            ResourceLocation id = ResourceLocation.tryParse(fluid.fluidId());
            if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) {
                return null;
            }
            Fluid found = BuiltInRegistries.FLUID.get(id);
            if (found == null || found == Fluids.EMPTY) {
                return null;
            }
            AEFluidKey key = AEFluidKey.of(found);
            if (key == null) {
                return null;
            }
            inputs.add(new SimpleInput(new GenericStack[]{new GenericStack(key, fluid.amount())}, 1L));
        }
        if (inputs.isEmpty()) {
            return null;
        }
        return new SimplePattern(outputKey, inputs.toArray(new IPatternDetails.IInput[0]),
                List.of(new GenericStack(outputKey, recipe.outputCount())));
    }

    /**
     * @return {@code null} when the pattern is not one of ours (let SCC answer),
     *         otherwise whether the job was accepted.
     */
    @Nullable
    public static Boolean push(NetworkBlockEntity be, List<SccAe2Recipe> recipes,
                               IPatternDetails pattern, KeyCounter[] inputs) {
        GenericStack primary = pattern.getPrimaryOutput();
        if (primary == null || !(primary.what() instanceof AEItemKey outputItem)) {
            return null;
        }
        SccAe2Recipe recipe = null;
        for (SccAe2Recipe candidate : recipes) {
            if (candidate.output().item() == outputItem.getItem()) {
                recipe = candidate;
                break;
            }
        }
        if (recipe == null) {
            return null;
        }
        NetworkView view = be.network();
        if (view == null || !view.online()) {
            return false;
        }
        Level level = be.getLevel();
        if (!(level instanceof ServerLevel server)) {
            return false;
        }

        // Only take the ingredients when everything fits (items and fluids).
        for (KeyCounter counter : inputs) {
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                if (entry.getKey() instanceof AEItemKey item) {
                    ItemStack stack = item.toStack((int) Math.min(Integer.MAX_VALUE, entry.getLongValue()));
                    if (!view.insert(stack, true).isEmpty()) {
                        return false;
                    }
                } else if (entry.getKey() instanceof AEFluidKey fluid) {
                    FluidStack stack = fluid.toStack((int) Math.min(Integer.MAX_VALUE, entry.getLongValue()));
                    if (!view.insertFluid(stack, true).isEmpty()) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
        for (KeyCounter counter : inputs) {
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                if (entry.getKey() instanceof AEItemKey item) {
                    ItemStack stack = item.toStack((int) Math.min(Integer.MAX_VALUE, entry.getLongValue()));
                    ItemStack rest = view.insert(stack, false);
                    if (!rest.isEmpty()) {
                        SccLog.warn("sccadd", "AE2 pushed more than the SCC network took: " + rest.getCount() + "x " + rest.getItem());
                    }
                } else if (entry.getKey() instanceof AEFluidKey fluid) {
                    FluidStack stack = fluid.toStack((int) Math.min(Integer.MAX_VALUE, entry.getLongValue()));
                    FluidStack rest = view.insertFluid(stack, false);
                    if (!rest.isEmpty()) {
                        SccLog.warn("sccadd", "AE2 pushed more fluid than the SCC network took: " + rest.getAmount() + "mb " + fluid.getFluid());
                    }
                }
            }
        }

        boolean asked = FuelOrders.request(server, be.getBlockPos(), view, recipe.output(), recipe.outputCount(), FuelOrders.AE2_ROW);
        if (!asked) {
            SccLog.warn("sccadd", "AE2 pushed the ingredients for " + recipe.output().encode()
                    + " but SCC could not start the job (no terminal or planner around?)");
        }
        return asked;
    }

    private record SimplePattern(AEItemKey definition, IPatternDetails.IInput[] inputs,
                                 List<GenericStack> outputs) implements IPatternDetails {
        @Override
        public AEItemKey getDefinition() {
            return this.definition;
        }

        @Override
        public IPatternDetails.IInput[] getInputs() {
            return this.inputs;
        }

        @Override
        public List<GenericStack> getOutputs() {
            return this.outputs;
        }
    }

    private record SimpleInput(GenericStack[] possibleInputs, long multiplier) implements IPatternDetails.IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return this.possibleInputs;
        }

        @Override
        public long getMultiplier() {
            return this.multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            for (GenericStack stack : this.possibleInputs) {
                if (stack.what().equals(input)) {
                    return true;
                }
            }
            return false;
        }

        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }
}
