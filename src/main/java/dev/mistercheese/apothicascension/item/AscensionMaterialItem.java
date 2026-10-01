// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.item;

import dev.mistercheese.apothicascension.AscensionRarity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Tier-aware first-party material for post-Mythic Ascension progression. */
public final class AscensionMaterialItem extends Item {
    private final AscensionRarity rarity;

    public AscensionMaterialItem(AscensionRarity rarity, Properties properties) {
        super(properties);
        this.rarity = rarity;
    }

    public AscensionRarity ascensionRarity() {
        return rarity;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.apothic_ascension.material." + rarity.key())
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
            "tooltip.apothic_ascension.material.tier",
            rarity.postMythicIndex(),
            AscensionRarity.APOTHEOTIC.postMythicIndex()
        ).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.apothic_ascension.material.use").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return rarity.sortIndex() >= AscensionRarity.EMPYREAN.sortIndex() || super.isFoil(stack);
    }
}
