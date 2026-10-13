package com.sccadd;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * All switches of the addon. Everything is server-side and safe to change at runtime
 * (the values are read fresh on every use, no caching).
 */
public final class SccAddConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue REUSE_CATALYSTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> REUSABLE_CATALYSTS;
    public static final ModConfigSpec.DoubleValue BREAKING_BUDGET_MS;
    public static final ModConfigSpec.DoubleValue BREAKING_TOTAL_BUDGET_MS;
    public static final ModConfigSpec.BooleanValue BREAKING_FIRE_DROPS_EVENT;
    public static final ModConfigSpec.BooleanValue BREAKING_DEFER_TOOL_SYNC;
    public static final ModConfigSpec.BooleanValue BREAKING_CACHE_LOOT_PARAMS;
    public static final ModConfigSpec.BooleanValue BREAKING_FIXED_LOOT;
    public static final ModConfigSpec.BooleanValue BREAKING_BATCH;
    public static final ModConfigSpec.IntValue BREAKING_BUFFER_BASE;
    public static final ModConfigSpec.IntValue BREAKING_ACCUMULATE;
    public static final ModConfigSpec.BooleanValue BREAKING_ACCUMULATE_PER_KEY;
    public static final ModConfigSpec.DoubleValue BREAKING_SPEEDUP;
    public static final ModConfigSpec.IntValue BREAKING_MAX_WAIT;
    public static final ModConfigSpec.DoubleValue BREAKING_SLOW_LOOT_MS;
    public static final ModConfigSpec.IntValue REQUESTER_MAX_ORDER;
    public static final ModConfigSpec.IntValue THINK_MS_PER_TICK;
    public static final ModConfigSpec.IntValue THINK_MAX_TASKS;
    public static final ModConfigSpec.IntValue THINK_MAX_RESTARTS;
    public static final ModConfigSpec.IntValue ORDER_MAX_AMOUNT;
    public static final ModConfigSpec.BooleanValue BREAKING_PROFILE;

    public static final ModConfigSpec.BooleanValue AE2_PATTERNS_IN_SCC;
    public static final ModConfigSpec.BooleanValue SCC_PATTERNS_IN_AE2;
    public static final ModConfigSpec.BooleanValue AE2_DEBUG_OFFER;
    public static final ModConfigSpec.BooleanValue AE2_BRIDGE_ON_RECEIVER;
    public static final ModConfigSpec.BooleanValue AE2_RECEIVER_READS_ME;
    public static final ModConfigSpec.BooleanValue AE2_FLUIDS;
    public static final ModConfigSpec.BooleanValue AE2_OFFER_REQUIRES_STOCK;
    public static final ModConfigSpec.BooleanValue AE2_FIBER_POWER;
    public static final ModConfigSpec.IntValue AE2_FIBER_FE_PER_TICK;
    public static final ModConfigSpec.BooleanValue AE2_FIBER_DEBUG;
    public static final ModConfigSpec.BooleanValue AE2_DISTRIBUTOR_IGNORES_AE2;
    public static final ModConfigSpec.BooleanValue PLANNER_SMART_PICK;

    public static final ModConfigSpec.BooleanValue ANALYSIS_RESILIENT_CACHE;
    public static final ModConfigSpec.BooleanValue TERMINAL_CLEAN_VARIANTS;
    public static final ModConfigSpec.IntValue MAGNET_RANGE;
    public static final ModConfigSpec.BooleanValue VILLAGE_ENERGY;
    public static final ModConfigSpec.IntValue VILLAGE_ENERGY_COST;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment(
                "Breaking Calculator performance.",
                "The Breaking Calculator simulates block breaks: loot roll, drop event, insertion.",
                "These switches make one simulated break much cheaper and cap how much time all",
                "Breaking Calculators together may spend inside one server tick.")
                .push("breaking");

        BREAKING_BUDGET_MS = builder
                .comment("Milliseconds one Breaking Calculator may spend per server tick (0.25 - 20).",
                        "The SCC default is 2 ms. Lower = smoother server, machines run a bit slower.")
                .defineInRange("budgetMs", 2.0, 0.25, 20.0);

        BREAKING_TOTAL_BUDGET_MS = builder
                .comment("Milliseconds ALL Breaking Calculators together may spend per server tick (0.25 - 50).",
                        "Stops ten Singularity calculators from stacking their budgets into one laggy tick.")
                .defineInRange("totalBudgetMs", 6.0, 0.25, 50.0);

        BREAKING_FIRE_DROPS_EVENT = builder
                .comment("Fire NeoForge's BlockDropsEvent for every simulated break (like a real player break).",
                        "On: enchantments and mods that hook block drops work (Boon of the Earth, Apotheosis...).",
                        "Off: less work per break, but those drop modifiers are ignored by the Breaking Calculator.")
                .define("fireDropsEvent", true);

        BREAKING_DEFER_TOOL_SYNC = builder
                .comment("Sync the worn tool once per tick instead of once per broken block.",
                        "Only affects the tool durability display of grades below Singularity.")
                .define("deferToolSync", true);

        BREAKING_CACHE_LOOT_PARAMS = builder
                .comment("Reuse the loot parameters (origin, tool, player, block state) across the blocks",
                        "of one tick instead of rebuilding them for every single block.")
                .define("cacheLootParams", true);

        BREAKING_FIXED_LOOT = builder
                .comment("Roll a loot table only once when it provably always drops the same items",
                        "(logs, stone, planks...) and copy the result for every further block.",
                        "Tables with any random or unknown part (fortune, chances, mod entries) are never cached.")
                .define("cacheFixedLoot", true);

        BREAKING_BATCH = builder
                .comment("Batch mode: instead of simulating every broken block, the calculator takes the blocks",
                        "from the network, sums their drops from dice tables learned from real results,",
                        "delivers the total once and spends the tool's durability as usual.",
                        "Off = SCC's behaviour, block by block.")
                .define("batch", true);

        BREAKING_BUFFER_BASE = builder
                .comment("Size of the calculator's hidden item buffer (items) for a Basic calculator.",
                        "It doubles with every tier: 256 / 512 / 1024 / 2048 / 4096 / 8192 by default.",
                        "Results that do not fit into the inventory above wait here; when it is full the calculator stops.")
                .defineInRange("bufferBase", 256, 16, 65536);

        BREAKING_ACCUMULATE = builder
                .comment("Batch size the calculator waits for before it works (in blocks).",
                        "The calculator banks the blocks it earns and delivers them in one batch, which costs",
                        "one real break per batch instead of one per block. Waiting never makes the machine",
                        "faster: what it earns is what the tier (or the Overdrive) allows. Output arrives in bursts.",
                        "0 = SCC's behaviour, block by block. Only used once a block's dice table is ready.")
                .defineInRange("accumulate", 64, 0, 512);

        BREAKING_ACCUMULATE_PER_KEY = builder
                .comment("Treat 'accumulate' as a size per item instead of a total for the call.",
                        "With several ores in the filters the total is split between them: on, each item in the",
                        "filters waits for the full 'accumulate' before its batch goes out (fewer real breaks).")
                .define("accumulatePerKey", true);

        BREAKING_SPEEDUP = builder
                .comment("Multiplier on the calculator's own rate (1.0 - 40.0). 1.0 = exactly what SCC allows.",
                        "Bigger batches must not change how fast the machine really is, so the batch mode banks",
                        "the tier's rate (and the Overdrive rate) times this factor. 1.0 keeps the tier balance",
                        "and the Chronon economy intact; raise it deliberately to make the machines themselves faster.")
                .defineInRange("speedup", 1.0, 1.0, 40.0);

        BREAKING_MAX_WAIT = builder
                .comment("Longest the calculator waits for a full batch, in ticks (0 = as long as the batch needs).",
                        "The wait is earned by the machine's own rate: a Singularity earning 64 blocks needs 40",
                        "ticks. This caps how late the drops can arrive on slow tiers. 20 ticks = 1 second.")
                .defineInRange("maxWaitTicks", 200, 0, 24000);

        BREAKING_SLOW_LOOT_MS = builder
                .comment("Write to logs/scc.log when one loot roll takes longer than this (ms). 0 = off.",
                        "Finds the tables that really cost time (modded ores, heavy drop events) instead of guessing.")
                .defineInRange("slowLootMs", 4.0, 0.0, 1000.0);

        BREAKING_PROFILE = builder
                .comment("Add fine-grained marks for the breaking path to /scc profile",
                        "(work, loot roll, drop event, insertion). Purely for measuring.")
                .define("profile", true);

        builder.pop();

        builder.comment(
                "Applied Energistics 2 integration.",
                "Requires AE2 and a Storage Connector / Network Antenna on an AE2 network.")
                .push("ae2");

        AE2_PATTERNS_IN_SCC = builder
                .comment("Show the ingredients of AE2 patterns in the SCC calculator.",
                        "AE2 craftable items then appear in the recipe tree with what the pattern really needs,",
                        "instead of one opaque 'AE2 can build it' step.")
                .define("patternsInScc", true);

        SCC_PATTERNS_IN_AE2 = builder
                .comment("Offer SCC recipes to AE2 as real patterns (with ingredients).",
                        "Lets AE2 plan the full tree through SCC instead of a leaf it cannot break down.")
                .define("sccPatternsInAe2", true);

        AE2_DEBUG_OFFER = builder
                .comment("Write the state of the 'what can SCC build' offer to logs/scc.log every 5 seconds.",
                        "Diagnosis aid: shows active/cpus/terminal/offer/emitted. Turn off when done.")
                .define("debugOffer", false);

        AE2_BRIDGE_ON_RECEIVER = builder
                .comment("The Wireless Receiver acts as an AE2 bridge, like the Network Antenna.",
                        "Put the receiver next to the AE2 network: it mounts the SCC network as ME storage",
                        "and offers the SCC recipes as real patterns - no antenna needed.")
                .define("bridgeOnReceiver", true);

        AE2_RECEIVER_READS_ME = builder
                .comment("The Wireless Receiver also reads the AE2 network it touches into the SCC network:",
                        "ME items count as SCC stock (terminals list them, recipes can use them).")
                .define("receiverReadsMe", true);

        AE2_FLUIDS = builder
                .comment("Bridge fluids between AE2 and SCC, both directions (items already work).",
                        "SCC fluids appear in the ME network; ME fluids appear in the SCC network.",
                        "A Storage Connector on an AE2 block uses the ME fluids; the receiver bridge",
                        "mounts the SCC fluids.")
                .define("fluids", true);

        AE2_OFFER_REQUIRES_STOCK = builder
                .comment("Only offer AE2 what SCC can really build with the network's current stock.",
                        "Uses the exact same reachability filter as the SCC terminal, so AE2 never",
                        "lists items that cannot be built right now. Turn off to offer everything",
                        "SCC could build if AE2 supplied the ingredients (hybrid mode).")
                .define("offerRequiresStock", true);

        AE2_FIBER_POWER = builder
                .comment("Let a Quartz Fiber feed the SCC network from the ME network (AE to FE).",
                        "Put an AE2 cable with a Quartz Fiber against any SCC network block (cable,",
                        "cable bus, storage, storage connector, controller, interface): the block pulls",
                        "AE out of the ME network the fiber touches and hands FE to the whole SCC network.",
                        "Only the Quartz Fiber connects - a plain AE2 cable next to an SCC block does not.",
                        "The ratio is AE2's own (2 FE per AE by default), the one the antenna uses the other",
                        "way around. The fiber only replaces what the network consumed, so it cannot drain",
                        "an ME network into a buffer that never fills. Does nothing while SCC 'power' is",
                        "off in scc-server.json.")
                .define("fiberPower", true);

        AE2_FIBER_FE_PER_TICK = builder
                .comment("Hard cap in FE per tick for each SCC network fed by fibers (0 = no cap).",
                        "The cap is per network and per tick, shared by all fibers on it.")
                .defineInRange("fiberFePerTick", 0, 0, Integer.MAX_VALUE);

        AE2_FIBER_DEBUG = builder
                .comment("Write what the fiber bridge does to logs/scc.log (at most one line every 5 s).")
                .define("fiberDebug", false);

        AE2_DISTRIBUTOR_IGNORES_AE2 = builder
                .comment("Keep the Stellar Distributor away from AE2 blocks that accept FE",
                        "(the Energy Acceptor part and the FE P2P tunnel on an AE2 cable bus).",
                        "The distributor would push into the ME network at its own rate - unlimited on",
                        "the Singularity tier - and the fiber would hand that power back to the SCC",
                        "network: an endless loop of energy in and out. The antenna stays as the normal",
                        "SCC to ME path (pull based, paid in FE per AE).")
                .define("distributorIgnoresAe2", true);

        builder.pop();

        builder.comment(
                "Recipe analysis cache.",
                "SCC throws its analysis away when mod versions or almost any setting changes.",
                "On big packs (ATM10: ~279k recipes) that costs 8-12 minutes without a usable system.",
                "With this on, such changes only mark the cache stale: it is used immediately and",
                "refreshed in the background, like SCC already does for changed recipes.")
                .push("analysis");

        ANALYSIS_RESILIENT_CACHE = builder
                .comment("Keep the analysis cache usable across mod updates and settings changes.")
                .define("resilientCache", true);

        builder.pop();

        builder.comment(
                "Planner selection.",
                "SCC sends planning requests (orders, AE2 offers) to a player's client, because only",
                "the client has the recipe viewer. Its own pick is always the first player in the list;",
                "with two players, an AFK or minimised first player stalls the flow for two minutes.",
                "With this on, the player who answered most recently is preferred and otherwise the",
                "choice rotates through the online players.")
                .push("planner");

        PLANNER_SMART_PICK = builder
                .comment("Pick a planner that actually answers instead of always the first player.")
                .define("smartPick", true);

        builder.pop();

        builder.comment("SCC terminal additions.").push("terminal");

        TERMINAL_CLEAN_VARIANTS = builder
                .comment("Hide craftable-only variants with enchantments, attribute changes, a custom name or lore.",
                        "Items the network really holds are always shown. Toggle with the 'Clean variants' bar button.")
                .define("cleanVariants", true);

        builder.pop();

        builder.comment("Processing Calculators and the machines they feed.").push("processing");

        REUSE_CATALYSTS = builder
                .comment("Count the presses (and other reusable catalysts) inside a calculator's machine as network stock,",
                        "and take a wrong press back out before a recipe that needs another one.",
                        "Without this, SCC orders a new press although the machine already holds one.")
                .define("reuseCatalysts", true);

        REUSABLE_CATALYSTS = builder
                .comment("Items a machine keeps and reuses instead of using up.")
                .defineListAllowEmpty("reusableCatalysts", List.of(
                        "ae2:logic_processor_press", "ae2:calculation_processor_press",
                        "ae2:engineering_processor_press", "ae2:silicon_press"), () -> "", o -> o instanceof String);

        builder.pop();

        builder.comment("Pocket terminal magnet.").push("magnet");

        MAGNET_RANGE = builder
                .comment("How far (in blocks) the magnet pulls dropped items into the SCC network.",
                        "SCC's own magnet only takes what touches you. 0 = off (SCC behaviour).")
                .defineInRange("range", 16, 0, 64);

        builder.pop();

        builder.comment("Requester block.").push("requester");

        REQUESTER_MAX_ORDER = builder
                .comment("The most items one automatic order plans at once (SCC: 9999).",
                        "The target stock itself can be far larger; the requester orders in steps of this size or the row's own step.",
                        "A huge single plan can take a long time to compute, so raise it with care.")
                .defineInRange("maxOrder", 100000, 1, 1_000_000_000);

        builder.pop();

        builder.comment("Order dialog and the Think button.").push("think");

        ORDER_MAX_AMOUNT = builder
                .comment("The largest amount the order dialog accepts (SCC: 9999).")
                .defineInRange("maxOrderAmount", 1_000_000, 1, 1_000_000_000);

        THINK_MS_PER_TICK = builder
                .comment("Milliseconds of client time per tick spent on a plan the Think button is working on.",
                        "More = finishes sooner, less = smoother game. 20 ticks per second.")
                .defineInRange("msPerTick", 6, 1, 40);

        THINK_MAX_TASKS = builder
                .comment("How many plans can be thinking or waiting at once.")
                .defineInRange("maxTasks", 6, 1, 24);

        THINK_MAX_RESTARTS = builder
                .comment("SCC plans one thing at a time and interrupts a plan when you open another.",
                        "An interrupted Think plan starts again; after this many restarts it gives up.")
                .defineInRange("maxRestarts", 40, 0, 1000);

        builder.pop();

        builder.comment("Village Chamber (Easy Villagers).").push("village");

        VILLAGE_ENERGY = builder
                .comment("The Singularity charges energy for what it does: 4,000 FE per kept trade and",
                        "per new villager put in its stock. The other tiers are free. Turn off to make",
                        "the Singularity free as well.")
                .define("singularityEnergy", true);

        VILLAGE_ENERGY_COST = builder
                .comment("What the Singularity charges per trade and per new villager, in FE.")
                .defineInRange("singularityCost", 4000, 0, 1_000_000_000);

        builder.pop();

        SPEC = builder.build();
    }

    private SccAddConfig() {
    }

    public static double budgetMs() {
        return BREAKING_BUDGET_MS.get();
    }

    public static double totalBudgetMs() {
        return BREAKING_TOTAL_BUDGET_MS.get();
    }

    public static boolean fireDropsEvent() {
        return BREAKING_FIRE_DROPS_EVENT.get();
    }

    public static boolean deferToolSync() {
        return BREAKING_DEFER_TOOL_SYNC.get();
    }

    public static boolean batchBreaking() {
        return BREAKING_BATCH.get();
    }

    public static int thinkMsPerTick() {
        return THINK_MS_PER_TICK.get();
    }

    public static int thinkMaxTasks() {
        return THINK_MAX_TASKS.get();
    }

    public static int thinkMaxRestarts() {
        return THINK_MAX_RESTARTS.get();
    }

    public static int orderMaxAmount() {
        return ORDER_MAX_AMOUNT.get();
    }

    public static int requesterMaxOrder() {
        return REQUESTER_MAX_ORDER.get();
    }

    public static int accumulateTarget() {
        return BREAKING_ACCUMULATE.get();
    }

    public static boolean accumulatePerKey() {
        return BREAKING_ACCUMULATE_PER_KEY.get();
    }

    public static double speedup() {
        return BREAKING_SPEEDUP.get();
    }

    public static int maxWaitTicks() {
        return BREAKING_MAX_WAIT.get();
    }

    public static double slowLootMs() {
        return BREAKING_SLOW_LOOT_MS.get();
    }

    public static int bufferBase() {
        return BREAKING_BUFFER_BASE.get();
    }

    public static boolean cacheDeterministicLoot() {
        return BREAKING_FIXED_LOOT.get();
    }

    public static boolean cacheLootParams() {
        return BREAKING_CACHE_LOOT_PARAMS.get();
    }

    public static boolean profile() {
        return BREAKING_PROFILE.get();
    }

    public static boolean ae2PatternsInScc() {
        return AE2_PATTERNS_IN_SCC.get();
    }

    public static boolean sccPatternsInAe2() {
        return SCC_PATTERNS_IN_AE2.get();
    }

    public static boolean ae2DebugOffer() {
        return AE2_DEBUG_OFFER.get();
    }

    public static boolean bridgeOnReceiver() {
        return AE2_BRIDGE_ON_RECEIVER.get();
    }

    public static boolean receiverReadsMe() {
        return AE2_RECEIVER_READS_ME.get();
    }

    public static boolean ae2Fluids() {
        return AE2_FLUIDS.get();
    }

    public static boolean offerRequiresStock() {
        return AE2_OFFER_REQUIRES_STOCK.get();
    }

    public static boolean ae2FiberPower() {
        return AE2_FIBER_POWER.get();
    }

    public static int ae2FiberFePerTick() {
        return AE2_FIBER_FE_PER_TICK.get();
    }

    public static boolean ae2FiberDebug() {
        return AE2_FIBER_DEBUG.get();
    }

    public static boolean distributorIgnoresAe2() {
        return AE2_DISTRIBUTOR_IGNORES_AE2.get();
    }

    public static boolean smartPlanner() {
        return PLANNER_SMART_PICK.get();
    }

    public static int magnetRange() {
        return MAGNET_RANGE.get();
    }

    public static boolean reuseCatalysts() {
        return REUSE_CATALYSTS.get();
    }

    public static List<? extends String> reusableCatalysts() {
        return REUSABLE_CATALYSTS.get();
    }

    public static boolean cleanVariants() {
        return TERMINAL_CLEAN_VARIANTS.get();
    }

    public static void setCleanVariants(boolean value) {
        TERMINAL_CLEAN_VARIANTS.set(value);
        TERMINAL_CLEAN_VARIANTS.save();
    }

    public static boolean resilientAnalysisCache() {
        return ANALYSIS_RESILIENT_CACHE.get();
    }

    public static boolean villageEnergy() {
        return VILLAGE_ENERGY.get();
    }

    public static long villageEnergyCost() {
        return VILLAGE_ENERGY_COST.get();
    }
}
