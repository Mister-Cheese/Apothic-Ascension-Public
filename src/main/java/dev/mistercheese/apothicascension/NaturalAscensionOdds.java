// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import net.minecraft.util.RandomSource;

/** Shared numerically-stable odds helpers for astronomically rare natural Ascension rolls. */
public final class NaturalAscensionOdds {
    private static final double LOG2_GATE = 20.0D;
    private static final double GATE_PROBABILITY = 1.0D / (1 << 20);

    private NaturalAscensionOdds() {}

    /**
     * Tests probability {@code 2^-log2Denominator}, then applies a bounded multiplicative luck
     * factor and a hard probability ceiling. The factorized gate avoids floating underflow even
     * when the effective denominator is far above Double.MAX_VALUE.
     */
    public static boolean passes(RandomSource random, double log2Denominator, double luckFactor, double probabilityCeiling) {
        if (random == null || !Double.isFinite(log2Denominator) || log2Denominator < 0.0D) return false;
        if (!Double.isFinite(probabilityCeiling) || !(probabilityCeiling > 0.0D)) return false;
        double safeLuck = Math.max(1.0D, Double.isFinite(luckFactor) ? luckFactor : 1.0D);
        double ceiling = Math.max(1.0E-9D, Math.min(0.5D, probabilityCeiling));
        double effectiveLog2 = log2Denominator - log2(safeLuck);
        effectiveLog2 = Math.max(effectiveLog2, -log2(ceiling));
        if (!(effectiveLog2 > 0.0D)) return random.nextDouble() < ceiling;

        while (effectiveLog2 > LOG2_GATE) {
            if (random.nextDouble() >= GATE_PROBABILITY) return false;
            effectiveLog2 -= LOG2_GATE;
        }
        return random.nextDouble() < Math.scalb(1.0D, -(int) Math.floor(effectiveLog2))
            * Math.pow(2.0D, -(effectiveLog2 - Math.floor(effectiveLog2)));
    }

    public static double boundedLuckFactor(float luck, double bonus, double saturation) {
        if (!(luck > 0.0F) || !Float.isFinite(luck)) return 1.0D;
        if (!Double.isFinite(bonus) || !Double.isFinite(saturation)) return 1.0D;
        double finiteLuck = Math.min(10_000.0D, luck);
        double safeBonus = Math.max(0.0D, Math.min(2.0D, bonus));
        double safeSaturation = Math.max(0.25D, saturation);
        return 1.0D + safeBonus * (1.0D - Math.exp(-finiteLuck / safeSaturation));
    }

    public static double log2(double value) {
        return Math.log(value) / Math.log(2.0D);
    }
}
