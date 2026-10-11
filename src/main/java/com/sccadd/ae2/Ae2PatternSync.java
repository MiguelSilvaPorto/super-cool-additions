package com.sccadd.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.compat.ae2.Ae2Hooks;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.StorageConnectorBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Reads the AE2 patterns of every grid an SCC Storage Connector touches and packs
 * them into a compact text block. The client turns that text back into real
 * recipes, so AE2 patterns show up in the SCC calculator with their ingredients
 * instead of a single opaque "AE2" step.
 *
 * <p>Line format: {@code P <output> <outCount> <count,opt|opt;count,opt|...>}.</p>
 */
public final class Ae2PatternSync {
    /** Hard caps so a huge AE2 grid cannot bloat the terminal sync packet. */
    public static final int MAX_PATTERNS = 400;
    public static final int MAX_CHARS = 96_000;
    public static final int MAX_OPTIONS = 12;
    public static final int MAX_INPUTS = 12;
    public static final int MAX_KEYS = 4_000;

    private Ae2PatternSync() {
    }

    public static String collect(ServerLevel level, NetworkView view) {
        try {
            StringBuilder out = new StringBuilder(4_096);
            Set<BlockPos> seen = new HashSet<>();
            int patterns = 0;

            for (StorageConnectorBlockEntity connector : view.storages()) {
                if (patterns >= MAX_PATTERNS || out.length() > MAX_CHARS) {
                    break;
                }
                BlockPos target = connector.ae2Target();
                if (target == null || !seen.add(target)) {
                    continue;
                }
                Object gridObject = Ae2Hooks.gridKey(level, target, connector.ae2Side());
                if (!(gridObject instanceof IGrid grid)) {
                    continue;
                }
                ICraftingService crafting = grid.getCraftingService();
                if (crafting == null) {
                    continue;
                }
                int keys = 0;
                for (AEKey key : crafting.getCraftables(k -> k instanceof AEItemKey)) {
                    if (++keys > MAX_KEYS || patterns >= MAX_PATTERNS || out.length() > MAX_CHARS) {
                        break;
                    }
                    for (IPatternDetails detail : crafting.getCraftingFor(key)) {
                        if (patterns >= MAX_PATTERNS || out.length() > MAX_CHARS) {
                            break;
                        }
                        if (encode(out, detail)) {
                            patterns++;
                        }
                    }
                }
            }
            return out.toString();
        } catch (Throwable failed) {
            return "";
        }
    }

    private static boolean encode(StringBuilder out, IPatternDetails detail) {
        GenericStack primary = detail.getPrimaryOutput();
        if (primary == null || !(primary.what() instanceof AEItemKey primaryItem)) {
            return false;
        }
        StackKey output = StackKey.of(primaryItem.toStack(1));
        if (output == null) {
            return false;
        }
        int outCount = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, primary.amount()));

        IPatternDetails.IInput[] inputs = detail.getInputs();
        if (inputs == null || inputs.length == 0) {
            return false;
        }
        StringBuilder encodedInputs = new StringBuilder(64);
        int used = 0;
        for (IPatternDetails.IInput input : inputs) {
            if (used >= MAX_INPUTS) {
                break;
            }
            GenericStack[] options = input.getPossibleInputs();
            if (options == null || options.length == 0) {
                return false;
            }
            List<String> encoded = new ArrayList<>(Math.min(options.length, MAX_OPTIONS));
            long count = 0L;
            for (GenericStack option : options) {
                if (!(option.what() instanceof AEItemKey optionItem)) {
                    continue;
                }
                StackKey optionKey = StackKey.of(optionItem.toStack(1));
                if (optionKey == null) {
                    continue;
                }
                if (count == 0L) {
                    count = Math.max(1L, option.amount());
                }
                encoded.add(optionKey.encode());
                if (encoded.size() >= MAX_OPTIONS) {
                    break;
                }
            }
            if (encoded.isEmpty()) {
                // A fluid or otherwise unknown input: better no recipe than a wrong one.
                return false;
            }
            count = Math.max(1L, count) * Math.max(1L, input.getMultiplier());
            if (used > 0) {
                encodedInputs.append(';');
            }
            encodedInputs.append(count);
            for (String option : encoded) {
                encodedInputs.append(',').append(option);
            }
            used++;
        }
        if (used == 0) {
            return false;
        }

        out.append("P\t").append(output.encode()).append('\t').append(outCount).append('\t').append(encodedInputs).append('\n');
        return true;
    }
}
