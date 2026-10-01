// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.affix.AffixType;
import dev.shadowsoffire.apotheosis.loot.LootController;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.world.item.ItemStack;

/** Runtime integrity check for generated Apotheosis items, using Apotheosis' public API directly. */
public final class RuntimeAffixInspector {
    public record Result(boolean complete, int actualSortIndex, int affixCount, String failure) {}

    private RuntimeAffixInspector() {}

    public static Result inspect(ItemStack stack, int expectedSortIndex, int expectedAffixCount) {
        if (stack == null || stack.isEmpty()) {
            return new Result(false, Integer.MIN_VALUE, 0, "empty stack");
        }

        int actualSort = RarityResolver.sortIndex(stack);
        if (actualSort != expectedSortIndex) {
            return new Result(false, actualSort, -1,
                "rarity sort " + printable(actualSort) + " != expected " + expectedSortIndex);
        }
        try {
            int count = AffixHelper.getAffixes(stack).size();
            if (expectedAffixCount >= 0 && count != expectedAffixCount) {
                return new Result(false, actualSort, count, "affixes " + count + "/" + expectedAffixCount);
            }
            return new Result(true, actualSort, count, null);
        }
        catch (RuntimeException ex) {
            return new Result(false, actualSort, -1, describe(ex));
        }
    }

    /** Stable, log-oriented affix id list for runtime diagnostic output. */
    public static String describeAffixes(ItemStack stack) {
        try {
            return AffixHelper.getAffixes(stack).keySet().stream()
                .map(holder -> holder.getId().toString())
                .sorted()
                .collect(Collectors.joining(",", "[", "]"));
        }
        catch (RuntimeException ex) {
            return "[inspect-error:" + describe(ex) + "]";
        }
    }

    /**
     * Diagnostic snapshot of the selected affix types and the compatible candidates that remain
     * after the final selection state. This is intentionally used only by qualification output;
     * production generation never consults it.
     */
    public static String describePoolState(ItemStack stack, LootRarity rarity, GenContext gen) {
        try {
            Map<AffixType, Integer> selected = new EnumMap<>(AffixType.class);
            for (var holder : AffixHelper.getAffixes(stack).keySet()) {
                AffixType type = holder.get().definition().type();
                selected.merge(type, 1, Integer::sum);
            }

            PoolWeight stat = poolWeight(LootController.getWeightedAffixes(stack, rarity, AffixType.STAT, gen));
            PoolWeight basic = poolWeight(LootController.getWeightedAffixes(stack, rarity, AffixType.BASIC_EFFECT, gen));
            PoolWeight ability = poolWeight(LootController.getWeightedAffixes(stack, rarity, AffixType.ABILITY, gen));
            return "selected{stat=" + selected.getOrDefault(AffixType.STAT, 0)
                + ",basic=" + selected.getOrDefault(AffixType.BASIC_EFFECT, 0)
                + ",ability=" + selected.getOrDefault(AffixType.ABILITY, 0)
                + "} remaining{stat=" + stat
                + ",basic=" + basic
                + ",ability=" + ability + "}";
        }
        catch (RuntimeException ex) {
            return "pool-state-error=" + describe(ex);
        }
    }

    private record PoolWeight(int candidates, int positive, int totalWeight) {
        @Override public String toString() {
            return candidates + "/" + positive + "/w" + totalWeight;
        }
    }

    private static PoolWeight poolWeight(java.util.List<net.minecraft.util.random.WeightedEntry.Wrapper<dev.shadowsoffire.apotheosis.affix.Affix>> entries) {
        int positive = 0;
        int total = 0;
        for (var entry : entries) {
            int weight = entry.getWeight().asInt();
            total += weight;
            if (weight > 0) positive++;
        }
        return new PoolWeight(entries.size(), positive, total);
    }

    private static String printable(int value) {
        return value == Integer.MIN_VALUE ? "unresolved" : Integer.toString(value);
    }

    private static String describe(RuntimeException ex) {
        String name = ex.getClass().getSimpleName();
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return name;
        if (message.length() > 140) message = message.substring(0, 140) + "…";
        return name + ": " + message;
    }
}
