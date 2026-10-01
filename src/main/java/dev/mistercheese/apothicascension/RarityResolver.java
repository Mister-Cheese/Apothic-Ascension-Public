// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import dev.mistercheese.apothicascension.item.AscensionMaterialItem;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Direct, compiler-checked bridge to Apotheosis rarity state. */
public final class RarityResolver {
    private static final ResourceLocation APOTHEOSIS_MYTHIC =
        ResourceLocation.fromNamespaceAndPath("apotheosis", "mythic");

    private RarityResolver() {}

    /** Returns a live rarity registry entry by exact id, or null when it is not currently loaded. */
    @Nullable
    public static LootRarity registryRarity(ResourceLocation id) {
        return id == null ? null : RarityRegistry.INSTANCE.getValue(id);
    }

    /** Returns the exact live registry id for a rarity, or null if the value is not registered. */
    @Nullable
    public static ResourceLocation rarityId(LootRarity rarity) {
        return rarity == null ? null : RarityRegistry.INSTANCE.getKey(rarity);
    }

    /** Exact identity check. Numeric sort indices are progression metadata, never ownership. */
    public static boolean isRarity(LootRarity rarity, ResourceLocation expectedId) {
        ResourceLocation actual = rarityId(rarity);
        return actual != null && actual.equals(expectedId);
    }

    /** True only for Apotheosis' canonical Mythic entry. */
    public static boolean isApotheosisMythic(LootRarity rarity) {
        return isRarity(rarity, APOTHEOSIS_MYTHIC);
    }

    /** Resolves one of our post-Mythic rarity entries by authored Ascension identity. */
    @Nullable
    public static LootRarity ascensionLootRarity(AscensionRarity rarity) {
        if (rarity == null) return null;
        return registryRarity(ResourceLocation.fromNamespaceAndPath(ApothicAscension.MODID, rarity.key()));
    }

    /** Resolves either a native Apotheosis rarity or one of our Ascension rarities by known key. */
    @Nullable
    public static LootRarity knownRarity(String key) {
        if (key == null || key.isBlank()) return null;
        AscensionRarity ascension = AscensionRarity.byId(key);
        ResourceLocation id = ascension == null
            ? ResourceLocation.fromNamespaceAndPath("apotheosis", key)
            : ResourceLocation.fromNamespaceAndPath(ApothicAscension.MODID, ascension.key());
        return registryRarity(id);
    }

    public static int sortIndex(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Integer.MIN_VALUE;
        DynamicHolder<LootRarity> holder = AffixHelper.getRarity(stack);
        return holder.isBound() ? holder.get().sortIndex() : Integer.MIN_VALUE;
    }

    /**
     * Resolves an Apotheosis rarity only when the rarity registry entry is actually owned by
     * Apothic Ascension.
     *
     * <p>Sort indices are intentionally not used as an ownership signal. Other mods and
     * datapacks are free to define their own rarity ladders, and a coincidentally matching
     * numeric sort index must never cause Ascension mechanics or presentation to claim a
     * foreign rarity.</p>
     */
    public static AscensionRarity ascensionRarity(LootRarity rarity) {
        if (rarity == null) return null;
        ResourceLocation id = rarityId(rarity);
        if (id == null || !ApothicAscension.MODID.equals(id.getNamespace())) return null;
        return AscensionRarity.byId(id.toString());
    }

    /** Resolves only the Apotheosis-backed portion of Ascension rarity state. */
    public static AscensionRarity apotheosisAscensionRarity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        DynamicHolder<LootRarity> holder = AffixHelper.getRarity(stack);
        return holder.isBound() ? ascensionRarity(holder.get()) : null;
    }

    /**
     * Resolves any post-Mythic rarity state that Ascension itself owns or extends.
     * Apotheosis affix rarity remains authoritative for gear; first-party materials and
     * extended gems then supply their explicit authored tier instead of falling through
     * to vanilla ItemStack rarity (which would incorrectly report Common).
     */
    public static AscensionRarity ascensionRarity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        AscensionRarity affixRarity = apotheosisAscensionRarity(stack);
        if (affixRarity != null) return affixRarity;

        if (stack.getItem() instanceof AscensionMaterialItem material) {
            return material.ascensionRarity();
        }

        int gemTier = GemTierData.get(stack);
        if (gemTier > GemTier.PERFECT.id()) {
            GemTier tier = GemTier.byId(gemTier);
            return AscensionRarity.byId(tier.key());
        }

        return null;
    }
}
