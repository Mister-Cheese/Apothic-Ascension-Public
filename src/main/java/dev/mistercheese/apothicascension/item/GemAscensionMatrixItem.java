// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Reusable catalyst for post-Perfect Gem Ascension. */
public final class GemAscensionMatrixItem extends Item {
    public GemAscensionMatrixItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.apothic_ascension.matrix.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.apothic_ascension.matrix.reusable").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
