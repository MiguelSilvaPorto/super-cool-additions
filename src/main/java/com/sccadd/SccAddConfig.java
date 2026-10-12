package com.sccadd;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * All switches of the addon. Everything is server-side and safe to change at runtime
 * (the values are read fresh on every use, no caching).
 */
public final class SccAddConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue BREAKING_BUDGET_MS;
    public static final ModConfigSpec.DoubleValue BREAKING_TOTAL_BUDGET_MS;
    public static final ModConfigSpec.BooleanValue BREAKING_FIRE_DROPS_EVENT;
    public static final ModConfigSpec.BooleanValue BREAKING_DEFER_TOOL_SYNC;
    public static final ModConfigSpec.BooleanValue BREAKING_CACHE_LOOT_PARAMS;
    public static final ModConfigSpec.BooleanValue BREAKING_FIXED_LOOT;
    public static final ModConfigSpec.BooleanValue BREAKING_BATCH;
    public static final ModConfigSpec.IntValue BREAKING_BUFFER_BASE;
    public static final ModConfigSpec.IntValue BREAKING_ACCUMULATE;
    public static final ModConfigSpec.IntValue REQUESTER_MAX_ORDER;
    public static final ModConfigSpec.BooleanValue BREAKING_PROFILE;

    public static final ModConfigSpec.BooleanValue AE2_PATTERNS_IN_SCC;
    public static final ModConfigSpec.BooleanValue SCC_PATTERNS_IN_AE2;
    public static final ModConfigSpec.BooleanValue AE2_DEBUG_OFFER;
    public static final ModConfigSpec.BooleanValue AE2_BRIDGE_ON_RECEIVER;
    public static final ModConfigSpec.BooleanValue AE2_RECEIVER_READS_ME;
    public static final ModConfigSpec.BooleanValue AE2_FLUIDS;
    public static final ModConfigSpec.BooleanValue AE2_OFFER_REQUIRES_STOCK;
    public static final ModConfigSpec.BooleanValue PLANNER_SMART_PICK;

    public static final ModConfigSpec.BooleanValue ANALYSIS_RESILIENT_CACHE;
    public static final ModConfigSpec.BooleanValue TERMINAL_CLEAN_VARIANTS;
    public static final ModConfigSpec.IntValue MAGNET_RANGE;

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
                        "Instead of working every tick with the few blocks that came due, it lets them pile up",
                        "(at most ~1 s) and then breaks them in one go: fewer, bigger batches, far less overhead.",
                        "Output arrives in bursts. 0 = work every tick as SCC does. Only used once a block's dice table is ready.")
                .defineInRange("accumulate", 64, 0, 512);

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
                .define("debugOffer", true);

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

    public static int requesterMaxOrder() {
        return REQUESTER_MAX_ORDER.get();
    }

    public static int accumulateTarget() {
        return BREAKING_ACCUMULATE.get();
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

    public static boolean smartPlanner() {
        return PLANNER_SMART_PICK.get();
    }

    public static int magnetRange() {
        return MAGNET_RANGE.get();
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
}
