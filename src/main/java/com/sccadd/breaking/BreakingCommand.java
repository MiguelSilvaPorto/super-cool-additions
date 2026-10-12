package com.sccadd.breaking;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.sccadd.SccAddConfig;
import de.scc.calculator.network.BreakingCalculatorBlockEntity;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /sccadd breaking}: what the Breaking Calculators are doing right now - rate, credit, keys,
 * buffer, the batch counters and the dice tables - plus {@code reset} and a {@code stress} mode to
 * measure a big batch on purpose.
 */
@EventBusSubscriber(modid = "sccadd")
public final class BreakingCommand {
    private static final int MAX_LINES = 24;

    private BreakingCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("sccadd")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("breaking")
                        .executes(context -> report(context.getSource()))
                        .then(Commands.literal("tables").executes(context -> tables(context.getSource())))
                        .then(Commands.literal("reset").executes(context -> reset(context.getSource())))
                        .then(Commands.literal("stress")
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 4096))
                                        .executes(context -> stress(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "blocks")))))));
    }

    private static int report(CommandSourceStack source) {
        say(source, "SCC Additions - Breaking Calculator");
        say(source, "batch: " + (SccAddConfig.batchBreaking() ? "on" : "off")
                + ", accumulate " + SccAddConfig.accumulateTarget()
                + (SccAddConfig.accumulatePerKey() ? " per item" : " per call")
                + ", speedup " + fmt(SccAddConfig.speedup()) + "x"
                + ", max wait " + SccAddConfig.maxWaitTicks() + " t");
        say(source, "budget: " + fmt(SccAddConfig.budgetMs()) + " ms/machine, " + fmt(SccAddConfig.totalBudgetMs())
                + " ms/tick shared, spent " + fmt(BreakingBudget.spentNanos() / 1_000_000.0) + " ms so far this tick");
        say(source, "since the server started: " + BreakStats.summary());
        List<BreakingTrack.Live> live = BreakingTrack.loaded();
        say(source, "machines loaded: " + live.size()
                + (SccAddConfig.cacheDeterministicLoot() ? ", fixed loot cached" : "")
                + (SccAddConfig.fireDropsEvent() ? ", drop event on" : ", drop event off"));
        for (int i = 0; i < live.size() && i < MAX_LINES; i++) {
            say(source, describe(live.get(i)));
        }
        if (live.size() > MAX_LINES) {
            say(source, "... and " + (live.size() - MAX_LINES) + " more");
        }
        return live.size();
    }

    private static String describe(BreakingTrack.Live live) {
        BreakingCalculatorBlockEntity be = live.be();
        StringBuilder out = new StringBuilder("- ");
        out.append(live.level().dimension().location()).append(' ').append(be.getBlockPos().toShortString())
                .append(' ').append(be.grade().name())
                .append(' ').append(be.status())
                .append(" rate ").append(fmt(be.currentRate())).append("/s");
        if (be.overdriveStep() > 0) {
            out.append(" overdrive ").append(be.overdriveStep())
                    .append(" (").append(be.currentChrononCost()).append(" Chronon/s)");
        }
        if (be instanceof BreakingMetrics metrics) {
            out.append(" credit ").append(fmt(metrics.sccadd$credit()))
                    .append('/').append(metrics.sccadd$earned())
                    .append(" keys ").append(metrics.sccadd$activeKeys())
                    .append(" buffer ").append(metrics.sccadd$bufferTotal()).append('/').append(metrics.sccadd$bufferCapacity());
            if (metrics.sccadd$batching()) {
                out.append(" batching");
            }
        }
        return out.toString();
    }

    private static int tables(CommandSourceStack source) {
        List<DropModels.TableInfo> tables = DropModels.snapshot();
        say(source, "dice tables: " + tables.size() + " (" + DropModels.READY + " real result(s) make one usable)");
        for (int i = 0; i < tables.size() && i < MAX_LINES; i++) {
            DropModels.TableInfo table = tables.get(i);
            say(source, "- " + table.block() + " + " + table.tool() + ": " + table.samples() + " sample(s)"
                    + (table.ready() ? ", ready" : ", learning for " + (table.learningMs() / 1000L) + " s"));
        }
        if (tables.size() > MAX_LINES) {
            say(source, "... and " + (tables.size() - MAX_LINES) + " more");
        }
        return tables.size();
    }

    private static int reset(CommandSourceStack source) {
        DropModels.clear();
        say(source, "dice tables dropped: the calculators are learning the real loot again");
        return 1;
    }

    private static int stress(CommandSourceStack source, int blocks) {
        List<BreakingTrack.Live> live = BreakingTrack.loaded();
        int done = 0;
        for (BreakingTrack.Live one : live) {
            if (one.be() instanceof BreakingMetrics metrics) {
                metrics.sccadd$stress(blocks);
                done++;
            }
        }
        say(source, "stress: " + done + " calculator(s) will work " + blocks
                + " block(s) on the next tick (watch /scc profile and logs/scc.log)");
        return done;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static void say(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }
}
