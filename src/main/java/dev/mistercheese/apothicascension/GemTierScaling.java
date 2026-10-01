// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import dev.shadowsoffire.apotheosis.socket.gem.GemView;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.MobEffectBonus;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.special.BloodyArrowBonus;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.special.LeechBlockBonus;
import dev.shadowsoffire.apotheosis.util.RadialUtil.RadialData;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * Semantic scaling rules for extended gem grades.
 *
 * <p>These functions are pure and JIT-friendly: no reflection, no allocation except for the
 * upstream immutable value records that actually need replacement, and no ambient thread state.</p>
 */
public final class GemTierScaling {
    private static final double FRACTION_EFFECT_CAP = 0.95D;
    private static final double GENERIC_MULTIPLIER_CAP = 10.0D;
    private static final int ENCHANTMENT_LEVEL_CAP = 127;
    private static final int EFFECT_AMPLIFIER_CAP = 9;
    private static final int EFFECT_DURATION_CAP = 20 * 60 * 10; // 10 minutes
    private static final int ACTIVE_COOLDOWN_FLOOR = 20; // one second, unless upstream is already faster
    private static final float FROZEN_DROP_BONUS_CAP = 8.0F; // +800%; bounds list growth per loot event
    private static final int RADIAL_DIMENSION_CAP = 11; // at most 121 blocks before upstream geometry rules

    private GemTierScaling() {}

    public static GemTier tier(GemView gem) {
        return gem == null ? GemTier.PERFECT : GemTierData.tier(gem.gemStack());
    }

    public static boolean extended(GemView gem) {
        return tier(gem) != GemTier.PERFECT;
    }

    public static double multiplier(GemView gem) {
        return tier(gem).multiplier();
    }

    public static double scaleSigned(double base, GemView gem) {
        if (!extended(gem) || !Double.isFinite(base)) return base;
        double m = multiplier(gem);
        double scaled = base > 0.0D ? base * m : base < 0.0D ? base / m : 0.0D;
        return Double.isFinite(scaled) ? scaled : base;
    }

    public static float scaleSigned(float base, GemView gem) {
        double scaled = scaleSigned((double) base, gem);
        if (scaled > Float.MAX_VALUE) return Float.MAX_VALUE;
        if (scaled < -Float.MAX_VALUE) return -Float.MAX_VALUE;
        return (float) scaled;
    }

    public static float scaleGenericAttributeValue(AttributeModifier.Operation operation, float base, GemView gem) {
        double scaled = scaleSigned(base, gem);
        if (!extended(gem) || operation == null) return (float) scaled;
        if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            || operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            scaled = clampSignedBasePreserving(base, scaled, 2.0D);
        }
        return (float) scaled;
    }

    public static int scaleEnchantment(int base, GemView gem) {
        if (!extended(gem) || base <= 0) return base;
        long scaled = Math.round(base * multiplier(gem));
        return (int) Math.max(base, Math.min(ENCHANTMENT_LEVEL_CAP, scaled));
    }

    /** Probability-like value: improves with grade but never leaves [0,1]. */
    public static float scaleChance(float base, GemView gem) {
        return (float) saturatingFraction(base, gem, 1.0D);
    }

    /** Damage-reduction/healing fractions: never introduce accidental immunity from scaling. */
    public static float scaleProtectionFraction(float base, GemView gem) {
        return (float) saturatingFraction(base, gem, FRACTION_EFFECT_CAP);
    }

    public static double scaleAttribute(
        Holder<Attribute> attribute,
        AttributeModifier.Operation operation,
        double base,
        GemView gem
    ) {
        double scaled = scaleSigned(base, gem);
        if (!extended(gem) || attribute == null || operation == null) return scaled;

        if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            if (attribute.equals(Attributes.MOVEMENT_SPEED)) return clampPositiveBasePreserving(base, scaled, 1.00D);
            if (attribute.equals(Attributes.ATTACK_SPEED)) return clampPositiveBasePreserving(base, scaled, 2.50D);
            if (attribute.equals(NeoForgeMod.SWIM_SPEED)) return clampPositiveBasePreserving(base, scaled, 2.00D);
            if (attribute.equals(ALObjects.Attributes.DRAW_SPEED)) return clampPositiveBasePreserving(base, scaled, 3.00D);
            if (attribute.equals(ALObjects.Attributes.ARROW_VELOCITY)) return clampPositiveBasePreserving(base, scaled, 2.00D);
            if (attribute.equals(ALObjects.Attributes.MINING_SPEED) || attribute.equals(Attributes.BLOCK_BREAK_SPEED)) {
                return clampPositiveBasePreserving(base, scaled, 5.00D);
            }
            if (attribute.equals(Attributes.GRAVITY) && scaled < 0.0D) return Math.max(scaled, -0.90D);
            return clampSignedBasePreserving(base, scaled, GENERIC_MULTIPLIER_CAP);
        }
        if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
            return clampSignedBasePreserving(base, scaled, GENERIC_MULTIPLIER_CAP);
        }
        if (operation == AttributeModifier.Operation.ADD_VALUE) {
            if (attribute.equals(Attributes.STEP_HEIGHT)) return clampPositiveBasePreserving(base, scaled, 4.0D);
            if (attribute.equals(Attributes.BLOCK_INTERACTION_RANGE)) return clampPositiveBasePreserving(base, scaled, 10.0D);
            if (attribute.equals(Attributes.ENTITY_INTERACTION_RANGE)) return clampPositiveBasePreserving(base, scaled, 8.0D);
        }
        return scaled;
    }

    public static MobEffectBonus.EffectData scaleEffect(MobEffectBonus.EffectData base, GemView gem) {
        if (base == null || !extended(gem)) return base;
        double root = Math.sqrt(multiplier(gem));
        int duration = (int) Math.min(EFFECT_DURATION_CAP, Math.max(base.duration(), Math.round(base.duration() * root)));
        int ampGain = (int) Math.floor(Math.log(multiplier(gem)) / Math.log(2.0D));
        int amplifier = Math.min(EFFECT_AMPLIFIER_CAP, Math.max(base.amplifier(), base.amplifier() + ampGain));
        int cooldown = scaleCooldown(base.cooldown(), gem);
        return new MobEffectBonus.EffectData(duration, amplifier, cooldown);
    }

    public static int scaleCooldown(int base, GemView gem) {
        if (!extended(gem) || base <= 0) return base;
        int scaled = (int) Math.round(base / Math.sqrt(multiplier(gem)));
        int floor = Math.min(base, ACTIVE_COOLDOWN_FLOOR);
        return Math.max(floor, Math.min(base, scaled));
    }

    public static BloodyArrowBonus.Data scaleBloodyArrow(BloodyArrowBonus.Data base, GemView gem) {
        if (base == null || !extended(gem)) return base;
        double root = Math.sqrt(multiplier(gem));
        float cost = (float) (base.healthCost() / root);
        float damage = base.dmgMultiplier();
        if (damage > 1.0F) damage = (float) Math.min(12.0D, 1.0D + (damage - 1.0D) * root);
        else damage = scaleSigned(damage, gem);
        return new BloodyArrowBonus.Data(cost, damage, scaleCooldown(base.cooldown(), gem));
    }

    public static LeechBlockBonus.Data scaleLeech(LeechBlockBonus.Data base, GemView gem) {
        if (base == null || !extended(gem)) return base;
        float heal = (float) Math.max(base.healFactor(), Math.min(2.0D, base.healFactor() * Math.sqrt(multiplier(gem))));
        return new LeechBlockBonus.Data(heal, scaleCooldown(base.cooldown(), gem));
    }

    public static float scaleFrozenDrops(float base, GemView gem) {
        if (!extended(gem) || base <= 0.0F) return base;
        float scaled = (float) (base * Math.sqrt(multiplier(gem)));
        return Math.max(base, Math.min(FROZEN_DROP_BONUS_CAP, scaled));
    }

    public static RadialData scaleRadial(RadialData base, GemView gem) {
        if (base == null || !extended(gem)) return base;
        int steps = Math.min(4, (tier(gem).id() + 2) / 3);
        int growth = steps * 2;
        return new RadialData(
            boundedOddGrowth(base.x(), growth),
            boundedOddGrowth(base.y(), growth),
            base.xOff(),
            base.yOff());
    }

    private static double saturatingFraction(double base, GemView gem, double extensionCap) {
        if (!extended(gem) || !Double.isFinite(base) || base <= 0.0D || base >= 1.0D) return base;
        double scaled = 1.0D - Math.pow(1.0D - base, multiplier(gem));
        double cap = Math.max(base, extensionCap);
        return Math.max(base, Math.min(cap, Math.min(1.0D, scaled)));
    }

    private static int boundedOddGrowth(int base, int growth) {
        if (base <= 0) return base;
        // Preserve an already-out-of-envelope upstream value rather than overflowing int addition
        // while trying to grow it. Authored dimensions below the cap follow the exact old path.
        if (base >= RADIAL_DIMENSION_CAP) return base;
        int candidate = base + Math.max(0, growth);
        if ((candidate & 1) == 0) candidate++;
        return Math.min(RADIAL_DIMENSION_CAP, candidate);
    }

    private static double clampPositiveBasePreserving(double base, double scaled, double cap) {
        if (scaled <= 0.0D) return scaled;
        return Math.max(base, Math.min(Math.max(base, cap), scaled));
    }

    private static double clampSignedBasePreserving(double base, double scaled, double magnitudeCap) {
        double cap = Math.max(Math.abs(base), magnitudeCap);
        return Math.max(-cap, Math.min(cap, scaled));
    }

}
