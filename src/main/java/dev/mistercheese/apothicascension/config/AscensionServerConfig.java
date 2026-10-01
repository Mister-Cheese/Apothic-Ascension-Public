// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative operational and bounded natural-generation configuration. */
public final class AscensionServerConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue NATURAL_ITEM_ASCENSION;
    private static final ModConfigSpec.BooleanValue NATURAL_GEM_ASCENSION;
    private static final ModConfigSpec.DoubleValue NATURAL_ITEM_CURVE_SCALE;
    private static final ModConfigSpec.DoubleValue NATURAL_GEM_CURVE_SCALE;
    private static final ModConfigSpec.DoubleValue NATURAL_FRONTIER_PENALTY_SCALE;
    private static final ModConfigSpec.DoubleValue NATURAL_ITEM_LUCK_BONUS;
    private static final ModConfigSpec.DoubleValue NATURAL_GEM_LUCK_BONUS;
    private static final ModConfigSpec.DoubleValue NATURAL_PROBABILITY_CEILING;
    private static final ModConfigSpec.BooleanValue COMBAT_PRESSURE;
    private static final ModConfigSpec.IntValue PROGRESSION_REFRESH_INTERVAL;
    private static final ModConfigSpec.BooleanValue ANNOUNCE_STAGE_UNLOCKS;
    private static final ModConfigSpec.IntValue APEX_PROCESS_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("generation");
        NATURAL_ITEM_ASCENSION = builder
            .comment("Allow genuine Apotheosis Mythic loot rolls to naturally ascend above Mythic, including the full Apotheotic tail.")
            .translation("config.apothic_ascension.natural_item_ascension")
            .define("naturalItemAscension", true);
        NATURAL_GEM_ASCENSION = builder
            .comment("Allow naturally generated Perfect Apotheosis gems to roll any post-Perfect Ascension grade through Apotheotic.")
            .translation("config.apothic_ascension.natural_gem_ascension")
            .define("naturalGemAscension", true);
        NATURAL_ITEM_CURVE_SCALE = builder
            .comment("Multiplier applied to natural-item log2 rarity exponents. Values above 1 make the super-exponential upper tail rarer; below 1 makes it less rare.")
            .translation("config.apothic_ascension.natural_item_curve_scale")
            .defineInRange("naturalItemCurveScale", 1.0D, 0.35D, 4.0D);
        NATURAL_GEM_CURVE_SCALE = builder
            .comment("Multiplier applied to natural-gem log2 rarity exponents. The default preserves the historical Legendary/Ancient/Forgotten odds and extends the curve through Apotheotic.")
            .translation("config.apothic_ascension.natural_gem_curve_scale")
            .defineInRange("naturalGemCurveScale", 1.0D, 0.35D, 4.0D);
        NATURAL_FRONTIER_PENALTY_SCALE = builder
            .comment("Multiplier on the quadratic soft-stage penalty. Progression changes sane probabilities but never turns the natural upper tail into a hard lock.")
            .translation("config.apothic_ascension.natural_frontier_penalty_scale")
            .defineInRange("naturalFrontierPenaltyScale", 1.0D, 0.0D, 4.0D);
        NATURAL_ITEM_LUCK_BONUS = builder
            .comment("Maximum additional natural-item odds multiplier contributed by Luck. Default 0.75 means Luck asymptotically approaches 1.75x rather than scaling without bound.")
            .translation("config.apothic_ascension.natural_item_luck_bonus")
            .defineInRange("naturalItemLuckBonus", 0.75D, 0.0D, 1.5D);
        NATURAL_GEM_LUCK_BONUS = builder
            .comment("Maximum additional natural-gem odds multiplier contributed by Luck. Default 0.50 means Luck asymptotically approaches 1.50x.")
            .translation("config.apothic_ascension.natural_gem_luck_bonus")
            .defineInRange("naturalGemLuckBonus", 0.50D, 0.0D, 1.0D);
        NATURAL_PROBABILITY_CEILING = builder
            .comment("Hard ceiling for any individual Ascension natural-roll probability after Luck. It can never be configured above 50%, preventing guaranteed-drop feedback loops.")
            .translation("config.apothic_ascension.natural_probability_ceiling")
            .defineInRange("naturalProbabilityCeiling", 0.35D, 0.001D, 0.50D);
        builder.pop();

        builder.push("progression");
        PROGRESSION_REFRESH_INTERVAL = builder
            .comment("Ticks between automatic inventory-backed progression refreshes per player. Work is de-phased across players.")
            .translation("config.apothic_ascension.progression_refresh_interval")
            .defineInRange("refreshIntervalTicks", 100, 20, 1200);
        ANNOUNCE_STAGE_UNLOCKS = builder
            .comment("Send the player a system message when persistent Ascension progression advances.")
            .translation("config.apothic_ascension.announce_stage_unlocks")
            .define("announceStageUnlocks", true);
        builder.pop();

        builder.push("workstations");
        APEX_PROCESS_TICKS = builder
            .comment("Server-authoritative Apex processing duration in ticks. Inputs are transaction-locked during this interval.")
            .translation("config.apothic_ascension.apex_process_ticks")
            .defineInRange("apexProcessTicks", 18, 5, 100);
        builder.pop();

        builder.push("combat");
        COMBAT_PRESSURE = builder
            .comment("Enable the per-player post-Pinnacle hostile/boss pressure model. Disabling this does not alter loot progression.")
            .translation("config.apothic_ascension.combat_pressure")
            .define("combatPressure", true);
        builder.pop();

        SPEC = builder.build();
    }

    public static void register(ModContainer container) { container.registerConfig(ModConfig.Type.SERVER, SPEC); }
    public static boolean naturalItemAscension() { return NATURAL_ITEM_ASCENSION.getAsBoolean(); }
    public static boolean naturalGemAscension() { return NATURAL_GEM_ASCENSION.getAsBoolean(); }
    public static double naturalItemCurveScale() { return NATURAL_ITEM_CURVE_SCALE.getAsDouble(); }
    public static double naturalGemCurveScale() { return NATURAL_GEM_CURVE_SCALE.getAsDouble(); }
    public static double naturalFrontierPenaltyScale() { return NATURAL_FRONTIER_PENALTY_SCALE.getAsDouble(); }
    public static double naturalItemLuckBonus() { return NATURAL_ITEM_LUCK_BONUS.getAsDouble(); }
    public static double naturalGemLuckBonus() { return NATURAL_GEM_LUCK_BONUS.getAsDouble(); }
    public static double naturalProbabilityCeiling() { return Math.min(0.5D, NATURAL_PROBABILITY_CEILING.getAsDouble()); }
    public static boolean combatPressure() { return COMBAT_PRESSURE.getAsBoolean(); }
    public static int progressionRefreshIntervalTicks() { return Math.max(1, PROGRESSION_REFRESH_INTERVAL.getAsInt()); }
    public static boolean announceStageUnlocks() { return ANNOUNCE_STAGE_UNLOCKS.getAsBoolean(); }
    public static int apexProcessTicks() { return Math.max(1, APEX_PROCESS_TICKS.getAsInt()); }

    private AscensionServerConfig() {}
}
