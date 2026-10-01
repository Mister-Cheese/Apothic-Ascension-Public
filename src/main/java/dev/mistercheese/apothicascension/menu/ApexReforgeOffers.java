// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.menu;

import dev.mistercheese.apothicascension.AscensionRarity;
import dev.mistercheese.apothicascension.RarityResolver;
import dev.mistercheese.apothicascension.compat.EquipmentInteropPolicy;
import dev.mistercheese.apothicascension.registry.ModItems;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootController;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.apotheosis.util.ApothMiscUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * Server-owned five-choice Apex reforging model.
 *
 * <p>Each choice is the best result seen after a progressively larger deterministic sample budget
 * (1/2/4/8/16 rolls). That makes later choices non-decreasing under the quality score without
 * mutating affix levels beyond the target rarity's normal generation rules. Costs scale 1x..5x.
 * The client only receives the resulting ItemStacks through ordinary menu slot synchronization.</p>
 */
public final class ApexReforgeOffers {
    public static final int OFFER_COUNT = 5;
    public static final int[] SAMPLE_BUDGETS = {1, 2, 4, 8, 16};
    public static final String REFORGE_SEED_TAG = "ApexReforgeSeed";

    public static boolean isReforgeInput(ItemStack gear, ItemStack material, ItemStack sigils) {
        return gear != null && !gear.isEmpty()
            && !LootCategory.forItem(gear).isNone()
            && targetAscensionRarity(material) != null
            && sigils != null && sigils.is(Apoth.Items.SIGIL_OF_REBIRTH);
    }

    /** Apex reforging is intentionally reserved for the two rarities already gated by the multiblock. */
    public static AscensionRarity targetAscensionRarity(ItemStack material) {
        AscensionRarity rarity = ModItems.rarityForMaterial(material);
        if (rarity == AscensionRarity.TRANSCENDENT || rarity == AscensionRarity.APOTHEOTIC) return rarity;
        return null;
    }

    public static LootRarity targetLootRarity(ItemStack material) {
        AscensionRarity expected = targetAscensionRarity(material);
        if (expected == null) return null;
        LootRarity rarity = RarityResolver.ascensionLootRarity(expected);
        if (rarity == null) return null;
        // The item itself must still be the registered material for that exact rarity. This prevents
        // a misconfigured foreign data pack from repointing our authored material at another rarity.
        var materialRarity = dev.shadowsoffire.apotheosis.loot.RarityRegistry
            .getMaterialRarity(material.getItem()).getOptional().orElse(null);
        return materialRarity == rarity ? rarity : null;
    }

    public static List<ItemStack> generate(Player player, ItemStack input, ItemStack material, int seed) {
        if (player == null || input == null || input.isEmpty()) return emptyOffers();
        LootRarity rarity = targetLootRarity(material);
        if (rarity == null || LootCategory.forItem(input).isNone()) return emptyOffers();

        if (seed == 0) return emptyOffers();
        long mixedSeed = Integer.toUnsignedLong(seed) << 32;
        mixedSeed ^= Integer.toUnsignedLong(BuiltInRegistries.ITEM.getKey(input.getItem()).hashCode());
        mixedSeed ^= ((long) rarity.sortIndex() << 17);
        RandomSource random = new XoroshiroRandomSource(mixedSeed);
        GenContext context = GenContext.forPlayer(random, player);

        List<ItemStack> offers = new ArrayList<>(OFFER_COUNT);
        ItemStack best = ItemStack.EMPTY;
        double bestScore = Double.NEGATIVE_INFINITY;
        int thresholdIndex = 0;

        for (int draw = 1; draw <= SAMPLE_BUDGETS[OFFER_COUNT - 1]; draw++) {
            ItemStack candidate = input.copy();
            candidate.setCount(1);
            candidate = LootController.createLootItem(candidate, rarity, context);
            if (candidate != null && !candidate.isEmpty()
                && EquipmentInteropPolicy.preservesForeignState(input, candidate)) {
                double score = qualityScore(candidate);
                // A malformed foreign affix must not win (or poison) AA's deterministic offer ranking.
                // Invalid candidates are ineligible, but draw thresholds still advance below so a
                // broken early draw cannot turn the cheapest offer into the final 16-roll best.
                if (Double.isFinite(score) && (best.isEmpty() || score > bestScore)) {
                    best = candidate.copy();
                    bestScore = score;
                }
            }
            if (draw == SAMPLE_BUDGETS[thresholdIndex]) {
                offers.add(best.copy());
                thresholdIndex++;
                if (thresholdIndex >= OFFER_COUNT) break;
            }
        }
        while (offers.size() < OFFER_COUNT) offers.add(best.copy());
        return List.copyOf(offers);
    }

    /**
     * Stable comparison score used only to select the best candidate from a bounded sample set.
     * Affix count dominates; normalized affix levels break ties within the same count.
     */
    public static double qualityScore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0D;
        try {
            var levels = AffixHelper.streamAffixes(stack).mapToDouble(instance -> instance.level()).toArray();
            double sum = 0.0D;
            for (double level : levels) {
                if (!Double.isFinite(level)) return Double.NEGATIVE_INFINITY;
                sum += level;
                if (!Double.isFinite(sum)) return Double.NEGATIVE_INFINITY;
            }
            double score = levels.length * 4.0D + sum;
            return Double.isFinite(score) ? score : Double.NEGATIVE_INFINITY;
        }
        catch (RuntimeException ex) {
            // Scoring is only a bounded best-of-N selector. A malformed affix component from a
            // foreign integration must not crash the Apex menu/server tick; it simply cannot be
            // trusted as a selectable candidate. Generation failures themselves are intentionally
            // not swallowed here, because those indicate a broader Apotheosis/datapack failure.
            return Double.NEGATIVE_INFINITY;
        }
    }

    public static Cost cost(ItemStack material, int choice) {
        AscensionRarity rarity = targetAscensionRarity(material);
        if (rarity == null || choice < 0 || choice >= OFFER_COUNT) return Cost.INVALID;

        // These are the authored one-reforge prices used by the normal Transcendent/Apotheotic
        // recipes. Apex choices divide that single-recipe price into fifths: I=1/5 ... V=5/5.
        // Scaling the authored price instead of multiplying it keeps every item cost stack-safe and
        // makes the fifth choice exactly equal to the normal recipe price.
        int tier = rarity.postMythicIndex();
        int normalMaterials = 1 + 3 * tier;
        int normalSigils = 3 + 3 * tier;
        int normalLevels = 40 + 25 * tier;
        int fifths = choice + 1;
        int materials = scaledFifths(normalMaterials, fifths);
        int sigils = scaledFifths(normalSigils, fifths);
        int levels = scaledFifths(normalLevels, fifths);
        return new Cost(materials, sigils, levels, ApothMiscUtil.getExpCostForSlot(levels, 0));
    }

    private static int scaledFifths(int normalCost, int fifths) {
        return Math.max(1, (normalCost * fifths + OFFER_COUNT - 1) / OFFER_COUNT);
    }

    public static boolean canAfford(Player player, ItemStack material, ItemStack sigils, int choice) {
        Cost cost = cost(material, choice);
        if (!cost.valid()) return false;
        if (player.isCreative()) return true;
        return material.getCount() >= cost.materials()
            && sigils.getCount() >= cost.sigils()
            && player.experienceLevel >= cost.levels();
    }

    private static List<ItemStack> emptyOffers() {
        return Collections.nCopies(OFFER_COUNT, ItemStack.EMPTY);
    }

    public record Cost(int materials, int sigils, int levels, int experiencePoints) {
        public static final Cost INVALID = new Cost(0, 0, 0, 0);
        public boolean valid() { return materials > 0 && sigils > 0 && levels > 0 && experiencePoints >= 0; }
    }

    private ApexReforgeOffers() {}
}
