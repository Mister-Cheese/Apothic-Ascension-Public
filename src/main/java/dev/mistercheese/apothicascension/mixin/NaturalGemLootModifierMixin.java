// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.mixin;

import dev.mistercheese.apothicascension.NaturalGemTierRoller;
import dev.shadowsoffire.apotheosis.loot.modifiers.GemLootModifier;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Adds extended grade only at the natural gem loot call site, with explicit typed context. */
@Mixin(value = GemLootModifier.class, remap = false)
public abstract class NaturalGemLootModifierMixin {
    @Redirect(
        method = "doApply",
        at = @At(value = "INVOKE", target = "Ldev/shadowsoffire/apotheosis/socket/gem/Gem;toStack(Ldev/shadowsoffire/apotheosis/socket/gem/Purity;)Lnet/minecraft/world/item/ItemStack;"),
        require = 1,
        allow = 1,
        remap = false)
    private ItemStack apothicAscension$gradeNaturalGem(
        Gem gem,
        Purity purity,
        ObjectArrayList<ItemStack> generatedLoot,
        LootContext loot,
        GenContext context
    ) {
        ItemStack stack = gem.toStack(purity);
        if (purity == Purity.PERFECT) {
            NaturalGemTierRoller.maybeUpgradePerfectNaturalDrop(stack, loot.getRandom(), context.luck());
        }
        return stack;
    }
}
