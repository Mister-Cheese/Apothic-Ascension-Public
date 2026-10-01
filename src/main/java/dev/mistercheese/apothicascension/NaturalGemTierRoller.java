// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import dev.mistercheese.apothicascension.config.AscensionServerConfig;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Natural-drop-only full Ascension gem roller with a super-exponential upper tail. */
public final class NaturalGemTierRoller {
    private NaturalGemTierRoller() {}

    public static ItemStack maybeUpgradePerfectNaturalDrop(ItemStack gemStack, RandomSource random, float luck) {
        if (!AscensionServerConfig.naturalGemAscension()) return gemStack;
        if (gemStack == null || gemStack.isEmpty() || random == null) return gemStack;
        // Fail closed at the primitive boundary instead of relying on the current mixin callers.
        // A future call site must not be able to attach Ascension grade state to an arbitrary item
        // or to a non-Perfect Apotheosis gem.
        if (!(gemStack.getItem() instanceof GemItem) || GemItem.getPurity(gemStack) != Purity.PERFECT) return gemStack;
        if (GemTierData.get(gemStack) != GemTier.PERFECT.id()) return gemStack;

        double luckFactor = boundedLuckFactor(luck);
        for (int index = GemTier.APOTHEOTIC.id(); index >= GemTier.LEGENDARY.id(); index--) {
            double exponent = naturalLog2Denominator(index) * AscensionServerConfig.naturalGemCurveScale();
            if (NaturalAscensionOdds.passes(random, exponent, luckFactor, AscensionServerConfig.naturalProbabilityCeiling())) {
                GemTierData.set(gemStack, GemTier.byId(index).id());
                return gemStack;
            }
        }
        return gemStack;
    }

    /**
     * Gem-grade denominator is 2^(i^2 + 3i). This exactly preserves Beta 6's first three odds:
     * Legendary 1/16, Ancient 1/1024, Forgotten 1/262144, then continues through Apotheotic.
     */
    static double naturalLog2Denominator(int postPerfectIndex) {
        if (postPerfectIndex <= 0) return Double.POSITIVE_INFINITY;
        double i = postPerfectIndex;
        return i * i + 3.0D * i;
    }

    public static double boundedLuckFactor(float luck) {
        return NaturalAscensionOdds.boundedLuckFactor(luck, AscensionServerConfig.naturalGemLuckBonus(), 10.0D);
    }
}
