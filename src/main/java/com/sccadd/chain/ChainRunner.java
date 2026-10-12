package com.sccadd.chain;

import de.scc.calculator.SccLog;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.network.CraftJob;
import de.scc.calculator.network.CraftStep;
import de.scc.calculator.network.NetworkView;
import de.scc.calculator.network.RequesterBlockEntity;
import de.scc.calculator.network.TerminalBlockEntity;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a {@link ChainData}: one job at a time, boxes taken in flow order starting after the one
 * that ran last. A box runs when its item is below its target; it does as many crafts as the stock
 * of its ingredients allows (capped by its batch), so a loop that needs "one recipe and a half" to
 * get going simply does what it can, hands over, and comes round again.
 */
public final class ChainRunner {
    private static final int RETRY_TICKS = 200;

    private CraftJob job;
    private int cursor = -1;
    private long retryAt;

    public void tick(RequesterBlockEntity.Row row, ChainData chain, NetworkView view, TerminalBlockEntity terminal, long gameTime, String label) {
        if (this.job != null) {
            if (!this.job.isFinished()) {
                row.state = "order";
                return;
            }
            if (this.job.isFailed()) {
                this.retryAt = gameTime + RETRY_TICKS;
                row.state = "jobfailed";
                SccLog.info("requester", label + ": chain job failed - " + this.job.status());
            }
            this.job = null;
        }
        if (gameTime < this.retryAt) {
            return;
        }
        if (terminal == null) {
            row.state = "noterminal";
            return;
        }
        List<ChainData.Node> flow = chain.flow();
        if (flow.isEmpty()) {
            row.state = "off";
            return;
        }
        boolean blocked = false;
        int size = flow.size();
        for (int k = 1; k <= size; k++) {
            int index = Math.floorMod(this.cursor + k, size);
            ChainData.Node node = flow.get(index);
            long crafts = this.craftsFor(node, view);
            if (crafts < 0) {
                continue;
            }
            if (crafts == 0) {
                blocked = true;
                continue;
            }
            CraftStep template = resolve(node, view);
            if (template == null) {
                continue;
            }
            CraftStep step = template.withCrafts(crafts, crafts * Math.max(1, template.outputCount()));
            CraftJob started = terminal.startJobTracked(step.encode() + "\n", null);
            if (started == null) {
                blocked = true;
                continue;
            }
            this.job = started;
            this.cursor = index;
            row.state = "order";
            return;
        }
        row.state = blocked ? "low" : "ok";
    }

    /** The box's recipe with every tag ingredient pinned to the item the network has most of. */
    private static CraftStep resolve(ChainData.Node node, NetworkView view) {
        CraftStep template = CraftStep.decode(node.step);
        if (template == null || node.alts.isEmpty()) {
            return template;
        }
        List<CraftStep.Ing> inputs = new ArrayList<>();
        for (int i = 0; i < template.inputs().size(); i++) {
            CraftStep.Ing input = template.inputs().get(i);
            String options = i < node.alts.size() ? node.alts.get(i) : "";
            StackKey best = input.item();
            long bestFree = view.free(best);
            if (!options.isEmpty()) {
                for (String code : options.split("\\|")) {
                    StackKey option = StackKey.decode(code);
                    if (option != null && view.free(option) > bestFree) {
                        best = option;
                        bestFree = view.free(option);
                    }
                }
            }
            inputs.add(new CraftStep.Ing(best, input.count(), input.consumed()));
        }
        return new CraftStep(template.recipeId(), template.typeId(), template.output(), template.outputCount(),
                template.crafts(), inputs, template.machineId(), template.fluids(), template.chemicals(),
                template.chance(), template.want(), template.returns());
    }

    /** -1: nothing to do for this box, 0: it wants to run but cannot yet, otherwise the crafts to run. */
    private long craftsFor(ChainData.Node node, NetworkView view) {
        if (node.step.isEmpty() || node.item.isEmpty()) {
            return -1;
        }
        StackKey key = StackKey.decode(node.item);
        CraftStep template = resolve(node, view);
        if (key == null || template == null) {
            return -1;
        }
        long missing = node.target - view.count(key);
        if (missing <= 0) {
            return -1;
        }
        long perCraft = Math.max(1, template.outputCount());
        long crafts = (missing + perCraft - 1) / perCraft;
        if (node.batch > 0) {
            crafts = Math.min(crafts, node.batch);
        }
        for (CraftStep.Ing input : template.inputs()) {
            if (input.consumed() && input.count() > 0) {
                crafts = Math.min(crafts, view.free(input.item()) / input.count());
            }
        }
        return Math.max(0L, crafts);
    }
}
