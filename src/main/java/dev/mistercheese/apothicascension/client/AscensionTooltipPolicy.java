// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.client;

import dev.mistercheese.apothicascension.AscensionRarity;
import dev.mistercheese.apothicascension.GemTier;
import dev.mistercheese.apothicascension.GemTierData;
import dev.mistercheese.apothicascension.RarityResolver;
import dev.mistercheese.apothicascension.item.AscensionMaterialItem;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.world.item.ItemStack;

/**
 * Single ownership boundary for Apothic Ascension tooltip presentation.
 *
 * <p>This policy is deliberately semantic, not visual. A stack is never claimed because it has
 * a vanilla rarity, because another mod happens to use a similar color, or because an unrelated
 * rarity happens to share an Ascension sort index. That keeps ordinary and third-party tooltips
 * untouched while ensuring explicitly-authored Ascension state remains recognizable even when a
 * foreign mod has attached additional data to the same stack.</p>
 */
public final class AscensionTooltipPolicy {
    public enum Scope {
        NONE,
        APOTHEOSIS_ONLY,
        ASCENSION_AFFIX,
        ASCENSION_GEM,
        ASCENSION_ITEM;

        public boolean isAscensionOwned() {
            return this == ASCENSION_AFFIX || this == ASCENSION_GEM || this == ASCENSION_ITEM;
        }
    }

    public record Decision(Scope scope, AscensionRarity rarity) {
        private static final Decision NONE = new Decision(Scope.NONE, null);
        private static final Decision APOTHEOSIS_ONLY = new Decision(Scope.APOTHEOSIS_ONLY, null);

        public boolean isAscensionOwned() {
            return scope.isAscensionOwned() && rarity != null;
        }
    }

    private AscensionTooltipPolicy() {}

    public static Decision classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Decision.NONE;

        // Explicit first-party state wins presentation ownership. A foreign mod attaching an
        // ordinary Apotheosis rarity to one of these stacks must not erase its authored identity.
        if (stack.getItem() instanceof AscensionMaterialItem material) {
            return new Decision(Scope.ASCENSION_ITEM, material.ascensionRarity());
        }

        int gemGrade = GemTierData.get(stack);
        if (gemGrade > GemTier.PERFECT.id()) {
            GemTier tier = GemTier.byId(gemGrade);
            AscensionRarity rarity = AscensionRarity.byId(tier.key());
            return rarity == null ? Decision.NONE : new Decision(Scope.ASCENSION_GEM, rarity);
        }

        DynamicHolder<LootRarity> holder = AffixHelper.getRarity(stack);
        if (!holder.isBound()) return Decision.NONE;

        AscensionRarity rarity = RarityResolver.ascensionRarity(holder.get());
        if (rarity == null) return Decision.APOTHEOSIS_ONLY;
        return new Decision(Scope.ASCENSION_AFFIX, rarity);
    }
}
