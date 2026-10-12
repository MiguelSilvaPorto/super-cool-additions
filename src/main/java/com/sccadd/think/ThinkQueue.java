package com.sccadd.think;

import com.sccadd.SccAddConfig;
import de.scc.calculator.SccLog;
import de.scc.calculator.calc.StackKey;
import de.scc.calculator.client.screen.NetworkTerminalScreen;
import de.scc.calculator.client.screen.SimTask;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "Think": plans a big order in the background while the player goes on. The plan is computed by SCC's
 * own planner (the same function the order dialog uses), stepped a few milliseconds per client tick;
 * when it is done the order dialog can be reopened with the finished plan, ready to confirm.
 *
 * <p>SCC runs one planner at a time and cancels a parked one when something else plans, so a task that
 * got interrupted is simply started again when the planner is free.</p>
 */
public final class ThinkQueue {
    public static final int PURPLE = 0xFFB06BFF;

    public enum State {
        THINKING, READY, FAILED
    }

    public static final class Task {
        public final StackKey item;
        public final long amount;
        final Map<StackKey, Long> stock;
        final List<StackKey> variants;
        final boolean pick;
        final boolean reuse;
        SimTask<Object> sim;
        public volatile State state = State.THINKING;
        public Object result;
        public int restarts;
        public final long startedAt = System.currentTimeMillis();
        public long finishedAt;
        public long busyMillis;

        Task(StackKey item, long amount, Map<StackKey, Long> stock, List<StackKey> variants, boolean pick, boolean reuse) {
            this.item = item;
            this.amount = amount;
            this.stock = stock;
            this.variants = variants;
            this.pick = pick;
            this.reuse = reuse;
        }

        public long seconds() {
            return ((this.state == State.THINKING ? System.currentTimeMillis() : this.finishedAt) - this.startedAt) / 1000L;
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static Method compute;
    private static boolean broken;

    private ThinkQueue() {
    }

    public static synchronized List<Task> tasks() {
        return new ArrayList<>(TASKS);
    }

    public static synchronized boolean hasTasks() {
        return !TASKS.isEmpty();
    }

    /** Starts thinking about an order. Returns false when the queue is full or SCC's planner is not reachable. */
    public static synchronized boolean start(StackKey item, long amount, Map<StackKey, Long> stock, List<StackKey> variants,
                                             boolean pick, boolean reuse) {
        if (broken || TASKS.size() >= SccAddConfig.thinkMaxTasks()) {
            return false;
        }
        TASKS.add(new Task(item, amount, stock, variants, pick, reuse));
        notice(Component.literal("Thinking about " + amount + "x " + item.display().getHoverName().getString()
                + " - you can keep playing; it shows up in the Autocraft list."));
        return true;
    }

    public static synchronized void cancel(Task task) {
        if (task.sim != null && !task.sim.isDone()) {
            task.sim.cancel();
        }
        TASKS.remove(task);
    }

    /** Called every client tick: steps the first task that is still thinking. */
    public static synchronized void tick() {
        if (TASKS.isEmpty() || broken) {
            return;
        }
        Task current = null;
        for (Task task : TASKS) {
            if (task.state == State.THINKING) {
                current = task;
                break;
            }
        }
        if (current == null) {
            return;
        }
        try {
            if (current.sim == null) {
                // Another planner is active (the order dialog's, for instance): do not cancel it.
                if (SimTask.parked()) {
                    return;
                }
                final Task running = current;
                current.sim = new SimTask<>("think " + running.amount + "x " + running.item.display().getHoverName().getString(),
                        () -> run(running));
            }
            long budget = SccAddConfig.thinkMsPerTick() * 1_000_000L;
            if (current.sim.step(budget)) {
                current.busyMillis += current.sim.busyMillis();
                Object result = current.sim.result();
                if (result != null) {
                    current.result = result;
                    current.state = State.READY;
                    current.finishedAt = System.currentTimeMillis();
                    SccLog.info("sccadd", "think: " + current.amount + "x " + current.item.display().getHoverName().getString()
                            + " planned in " + current.busyMillis + " ms of work over " + current.seconds() + " s");
                    notice(Component.literal("Done thinking: " + current.amount + "x "
                            + current.item.display().getHoverName().getString() + " - open the Autocraft list to review it."));
                } else if (current.sim.error() == null && current.restarts < SccAddConfig.thinkMaxRestarts()) {
                    // Interrupted by another plan: start again when the planner is free.
                    current.restarts++;
                    current.sim = null;
                } else {
                    current.state = State.FAILED;
                    current.finishedAt = System.currentTimeMillis();
                    SccLog.warn("sccadd", "think failed for " + current.item + ": " + current.sim.error());
                }
            }
        } catch (Throwable failed) {
            current.state = State.FAILED;
            current.finishedAt = System.currentTimeMillis();
            SccLog.warn("sccadd", "think failed: " + failed);
        }
    }

    private static Object run(Task task) {
        try {
            if (compute == null) {
                Class<?> dialog = Class.forName("de.scc.calculator.client.screen.OrderDialog");
                compute = dialog.getDeclaredMethod("computePlan", StackKey.class, long.class, Map.class, List.class,
                        boolean.class, boolean.class);
                compute.setAccessible(true);
            }
            return compute.invoke(null, task.item, task.amount, task.stock, task.variants, task.pick, task.reuse);
        } catch (ReflectiveOperationException failed) {
            broken = true;
            SccLog.warn("sccadd", "think: SCC's planner is not reachable: " + failed);
            return null;
        }
    }

    /** Opens the order dialog for a finished task with its plan already in place, and drops the task. */
    public static synchronized void resume(Task task) {
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof NetworkTerminalScreen<?> terminal)) {
            return;
        }
        if (task.state != State.READY) {
            notice(Component.literal("Still thinking (" + task.seconds() + " s)."));
            return;
        }
        try {
            terminal.openOrderFor(task.item);
            Field dialogField = NetworkTerminalScreen.class.getDeclaredField("orderDialog");
            dialogField.setAccessible(true);
            Object dialog = dialogField.get(terminal);
            Class<?> type = dialog.getClass();
            Field planned = type.getDeclaredField("planned");
            planned.setAccessible(true);
            planned.set(dialog, task.result);
            Field amount = type.getDeclaredField("amount");
            amount.setAccessible(true);
            amount.set(dialog, Long.toString(task.amount));
            Method plannedItem = task.result.getClass().getDeclaredMethod("item");
            plannedItem.setAccessible(true);
            Object picked = plannedItem.invoke(task.result);
            if (picked instanceof StackKey key) {
                Field item = type.getDeclaredField("item");
                item.setAccessible(true);
                item.set(dialog, key);
            }
            TASKS.remove(task);
        } catch (ReflectiveOperationException failed) {
            SccLog.warn("sccadd", "think: could not reopen the order: " + failed);
        }
    }

    private static void notice(Component text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(text, true);
        }
    }

    /** A row for SCC's running-jobs panel (a private record there, so it is built by reflection). */
    public static Object entryFor(Task task) {
        try {
            Class<?> entry = Class.forName("de.scc.calculator.client.screen.JobsPanel$Entry");
            Constructor<?> constructor = null;
            for (Constructor<?> candidate : entry.getDeclaredConstructors()) {
                if (candidate.getParameterCount() == 9) {
                    constructor = candidate;
                }
            }
            if (constructor == null) {
                return null;
            }
            constructor.setAccessible(true);
            String name = task.item.display().getHoverName().getString();
            String title = switch (task.state) {
                case THINKING -> "Thinking: " + task.amount + "x " + name;
                case READY -> "Ready: " + task.amount + "x " + name;
                case FAILED -> "Could not plan: " + task.amount + "x " + name;
            };
            String subtitle = switch (task.state) {
                case THINKING -> task.seconds() + " s" + (task.restarts > 0 ? " - restarted " + task.restarts + "x" : "")
                        + " - right-click to cancel";
                case READY -> "Click to review the plan and start it";
                case FAILED -> "Right-click to remove";
            };
            return constructor.newInstance(task.item, title, subtitle, task.state == State.FAILED ? 0xFFE05555 : PURPLE,
                    List.of(), -1, -1, "", -1.0F);
        } catch (ReflectiveOperationException failed) {
            return null;
        }
    }
}
