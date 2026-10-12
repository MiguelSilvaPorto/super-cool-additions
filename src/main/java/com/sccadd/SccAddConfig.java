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
    public static final ModConfigSpec.BooleanValue BREAKING_PROFILE;

    public static final ModConfigSpec.BooleanValue AE2_PATTERNS_IN_SCC;
    public static final ModConfigSpec.BooleanValue SCC_PATTERNS_IN_AE2;
    public static final ModConfigSpec.BooleanValue AE2_DEBUG_OFFER;
    public static final ModConfigSpec.BooleanValue PLANNER_SMART_PICK;

    public static final ModConfigSpec.BooleanValue ANALYSIS_RESILIENT_CACHE;

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

    public static boolean smartPlanner() {
        return PLANNER_SMART_PICK.get();
    }

    public static boolean resilientAnalysisCache() {
        return ANALYSIS_RESILIENT_CACHE.get();
    }
}
