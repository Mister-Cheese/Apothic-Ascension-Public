// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only presentation controls. None of these settings affect gameplay or server state. */
public final class AscensionClientConfig {
    public enum TooltipPresentationMode {
        FULL,
        COMPACT,
        OFF
    }

    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.EnumValue<TooltipPresentationMode> TOOLTIP_PRESENTATION_MODE;
    private static final ModConfigSpec.BooleanValue SHOW_ASCENSION_RARITY_LABELS;
    private static final ModConfigSpec.BooleanValue DECORATE_ASCENSION_ITEM_NAMES;
    private static final ModConfigSpec.BooleanValue DECORATE_ASCENSION_TOOLTIP_FRAMES;

    private static final ModConfigSpec.BooleanValue RENDER_MAGIC_CIRCLES;
    private static final ModConfigSpec.BooleanValue RENDER_APEX_SEAL;
    private static final ModConfigSpec.BooleanValue RENDER_PYLON_RINGS;
    private static final ModConfigSpec.BooleanValue RENDER_APEX_ORB;
    private static final ModConfigSpec.BooleanValue RENDER_RARITY_ITEM_CIRCLES;
    private static final ModConfigSpec.DoubleValue CIRCLE_GLOW_STRENGTH;
    private static final ModConfigSpec.DoubleValue CIRCLE_OPACITY;
    private static final ModConfigSpec.DoubleValue APEX_ROTATION_SPEED;
    private static final ModConfigSpec.DoubleValue PYLON_RING_ROTATION_SPEED;
    private static final ModConfigSpec.DoubleValue APEX_ORB_ROTATION_SPEED;
    private static final ModConfigSpec.DoubleValue RARITY_ROTATION_SPEED;
    private static final ModConfigSpec.IntValue APEX_ACTIVATION_TICKS;
    private static final ModConfigSpec.IntValue APEX_ORB_ACTIVATION_TICKS;
    private static final ModConfigSpec.IntValue APEX_ORB_PROXIMITY_RADIUS;
    private static final ModConfigSpec.IntValue CIRCLE_RENDER_DISTANCE;
    private static final ModConfigSpec.IntValue RARITY_SCAN_INTERVAL;
    private static final ModConfigSpec.BooleanValue REDUCED_MOTION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("tooltips");
        TOOLTIP_PRESENTATION_MODE = builder
            .comment(
                "Controls only Apothic Ascension-owned tooltip presentation.",
                "FULL shows the optional rarity label plus enabled name/frame decoration.",
                "COMPACT keeps enabled name/frame decoration but suppresses the separate rarity label.",
                "OFF disables Ascension rarity/name/frame decoration without changing item mechanics or foreign tooltips."
            )
            .translation("config.apothic_ascension.tooltip_presentation_mode")
            .defineEnum("presentationMode", TooltipPresentationMode.FULL);
        SHOW_ASCENSION_RARITY_LABELS = builder
            .comment(
                "Show a dedicated rarity line for Ascension-owned rarities in FULL mode.",
                "Common is never rendered by Apothic Ascension, and stock Apotheosis/third-party rarities are never claimed."
            )
            .translation("config.apothic_ascension.show_ascension_rarity_labels")
            .define("showAscensionRarityLabels", true);
        DECORATE_ASCENSION_ITEM_NAMES = builder
            .comment(
                "Add Ascension-owned glyph decoration around the existing item-name component.",
                "The original component tree and its styles are preserved; foreign title content is not flattened or replaced."
            )
            .translation("config.apothic_ascension.decorate_ascension_item_names")
            .define("decorateAscensionItemNames", true);
        DECORATE_ASCENSION_TOOLTIP_FRAMES = builder
            .comment("Use Ascension rarity colors for the tooltip frame/background of Ascension-owned stacks.")
            .translation("config.apothic_ascension.decorate_ascension_tooltip_frames")
            .define("decorateAscensionTooltipFrames", true);
        builder.pop();

        builder.push("visuals");
        RENDER_MAGIC_CIRCLES = builder
            .comment("Master switch for first-party Apothic Ascension magic-circle rendering.")
            .translation("config.apothic_ascension.render_magic_circles")
            .define("renderMagicCircles", true);
        RENDER_APEX_SEAL = builder
            .comment("Render the full Apex seal beneath a valid Apex Ascension multiblock.")
            .translation("config.apothic_ascension.render_apex_seal")
            .define("renderApexSeal", true);
        RENDER_PYLON_RINGS = builder
            .comment("Render the two small emissive gyroscopic rings above each Ascension Pylon.")
            .translation("config.apothic_ascension.render_pylon_rings")
            .define("renderPylonRings", true);
        RENDER_APEX_ORB = builder
            .comment("Render the three-tier emissive seal sphere above the Apex Ascension Bench.")
            .translation("config.apothic_ascension.render_apex_orb")
            .define("renderApexOrb", true);
        RENDER_RARITY_ITEM_CIRCLES = builder
            .comment("Render tier-scaled circles beneath dropped post-Mythic affix items.")
            .translation("config.apothic_ascension.render_rarity_item_circles")
            .define("renderRarityItemCircles", true);
        CIRCLE_GLOW_STRENGTH = builder
            .comment("Visual emissive/glow layering strength. Higher values can look substantially brighter with bloom-capable shaders.")
            .translation("config.apothic_ascension.circle_glow_strength")
            .defineInRange("circleGlowStrength", 1.15D, 0.0D, 2.5D);
        CIRCLE_OPACITY = builder
            .comment("Overall opacity multiplier for magic circles.")
            .translation("config.apothic_ascension.circle_opacity")
            .defineInRange("circleOpacity", 0.78D, 0.05D, 1.0D);
        APEX_ROTATION_SPEED = builder
            .comment("Apex seal rotation in degrees per game tick. The Beta 6 value was 0.35.")
            .translation("config.apothic_ascension.apex_rotation_speed")
            .defineInRange("apexRotationDegreesPerTick", 0.22D, 0.0D, 2.0D);
        PYLON_RING_ROTATION_SPEED = builder
            .comment("Pylon ring rotation in degrees per game tick.")
            .translation("config.apothic_ascension.pylon_ring_rotation_speed")
            .defineInRange("pylonRingRotationDegreesPerTick", 0.48D, 0.0D, 3.0D);
        APEX_ORB_ROTATION_SPEED = builder
            .comment("Base rotation speed for the three-tier Apex bench seal sphere, in degrees per game tick.")
            .translation("config.apothic_ascension.apex_orb_rotation_speed")
            .defineInRange("apexOrbRotationDegreesPerTick", 0.72D, 0.0D, 4.0D);
        RARITY_ROTATION_SPEED = builder
            .comment("Dropped-rarity circle rotation in degrees per game tick.")
            .translation("config.apothic_ascension.rarity_rotation_speed")
            .defineInRange("rarityRotationDegreesPerTick", 0.32D, 0.0D, 3.0D);
        APEX_ACTIVATION_TICKS = builder
            .comment("Ticks for the Apex floor seal to grow from the central bench to full size. Five ticks is approximately one quarter second.")
            .translation("config.apothic_ascension.apex_activation_ticks")
            .defineInRange("apexActivationTicks", 5, 1, 40);
        APEX_ORB_ACTIVATION_TICKS = builder
            .comment("Ticks for the dormant Apex bench seal to lift and unfold into its three-axis sphere when a player approaches.")
            .translation("config.apothic_ascension.apex_orb_activation_ticks")
            .defineInRange("apexOrbActivationTicks", 10, 1, 60);
        APEX_ORB_PROXIMITY_RADIUS = builder
            .comment("Client-side player proximity radius, in blocks, that wakes the Apex bench seal sphere. This is visual only.")
            .translation("config.apothic_ascension.apex_orb_proximity_radius")
            .defineInRange("apexOrbProximityRadius", 6, 2, 24);
        CIRCLE_RENDER_DISTANCE = builder
            .comment("Maximum client render/scan radius in blocks for dropped-item magic circles.")
            .translation("config.apothic_ascension.circle_render_distance")
            .defineInRange("circleRenderDistance", 32, 8, 96);
        RARITY_SCAN_INTERVAL = builder
            .comment("Client ticks between bounded nearby dropped-item scans. Higher values reduce scan frequency.")
            .translation("config.apothic_ascension.rarity_scan_interval")
            .defineInRange("rarityCircleScanIntervalTicks", 8, 2, 40);
        REDUCED_MOTION = builder
            .comment("Reduce circle animation. Circles remain visible but activation growth and rotation are minimized.")
            .translation("config.apothic_ascension.reduced_motion")
            .define("reducedMotion", false);
        builder.pop();
        SPEC = builder.build();
    }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    public static TooltipPresentationMode tooltipPresentationMode() { return TOOLTIP_PRESENTATION_MODE.get(); }
    public static boolean showAscensionRarityLabels() {
        return tooltipPresentationMode() == TooltipPresentationMode.FULL && SHOW_ASCENSION_RARITY_LABELS.getAsBoolean();
    }
    public static boolean decorateAscensionItemNames() {
        return tooltipPresentationMode() != TooltipPresentationMode.OFF && DECORATE_ASCENSION_ITEM_NAMES.getAsBoolean();
    }
    public static boolean decorateAscensionTooltipFrames() {
        return tooltipPresentationMode() != TooltipPresentationMode.OFF && DECORATE_ASCENSION_TOOLTIP_FRAMES.getAsBoolean();
    }

    public static boolean renderMagicCircles() { return RENDER_MAGIC_CIRCLES.getAsBoolean(); }
    public static boolean renderApexSeal() { return RENDER_APEX_SEAL.getAsBoolean(); }
    public static boolean renderPylonRings() { return RENDER_PYLON_RINGS.getAsBoolean(); }
    public static boolean renderApexOrb() { return RENDER_APEX_ORB.getAsBoolean(); }
    public static boolean renderRarityItemCircles() { return RENDER_RARITY_ITEM_CIRCLES.getAsBoolean(); }
    public static float circleGlowStrength() { return CIRCLE_GLOW_STRENGTH.get().floatValue(); }
    public static float circleOpacity() { return CIRCLE_OPACITY.get().floatValue(); }
    public static float apexRotationSpeed() { return APEX_ROTATION_SPEED.get().floatValue(); }
    public static float pylonRingRotationSpeed() { return PYLON_RING_ROTATION_SPEED.get().floatValue(); }
    public static float apexOrbRotationSpeed() { return APEX_ORB_ROTATION_SPEED.get().floatValue(); }
    public static float rarityRotationSpeed() { return RARITY_ROTATION_SPEED.get().floatValue(); }
    public static int apexActivationTicks() { return APEX_ACTIVATION_TICKS.getAsInt(); }
    public static int apexOrbActivationTicks() { return APEX_ORB_ACTIVATION_TICKS.getAsInt(); }
    public static int apexOrbProximityRadius() { return APEX_ORB_PROXIMITY_RADIUS.getAsInt(); }
    public static int circleRenderDistance() { return CIRCLE_RENDER_DISTANCE.getAsInt(); }
    public static int rarityScanIntervalTicks() { return RARITY_SCAN_INTERVAL.getAsInt(); }
    public static boolean reducedMotion() { return REDUCED_MOTION.getAsBoolean(); }

    private AscensionClientConfig() {}
}
