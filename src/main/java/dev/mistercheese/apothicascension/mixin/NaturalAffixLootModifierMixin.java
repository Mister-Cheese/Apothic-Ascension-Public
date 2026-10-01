// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.mixin;

import dev.mistercheese.apothicascension.NaturalItemRarityRoller;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.modifiers.AffixLootModifier;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Natural rarity extension with explicit LootContext; no ambient push/pop state. */
@Mixin(value = AffixLootModifier.class, remap = false)
public abstract class NaturalAffixLootModifierMixin {
    @Redirect(
        method = "doApply",
        at = @At(value = "INVOKE", target = "Ldev/shadowsoffire/apotheosis/loot/LootRarity;randomFromHolders(Ldev/shadowsoffire/apotheosis/tiers/GenContext;Ljava/util/Set;)Ldev/shadowsoffire/apotheosis/loot/LootRarity;"),
        require = 1,
        allow = 1,
        remap = false)
    private LootRarity apothicAscension$randomFromHolders(
        GenContext gen,
        Set<DynamicHolder<LootRarity>> holders,
        ObjectArrayList<ItemStack> generatedLoot,
        LootContext loot,
        GenContext methodContext
    ) {
        return NaturalItemRarityRoller.maybeAscend(LootRarity.randomFromHolders(gen, holders), loot, methodContext);
    }
    @Redirect(
        method = "doApply",
        at = @At(value = "INVOKE", target = "Ldev/shadowsoffire/apotheosis/loot/LootRarity;random(Ldev/shadowsoffire/apotheosis/tiers/GenContext;Ljava/util/Set;)Ldev/shadowsoffire/apotheosis/loot/LootRarity;"),
        require = 1,
        allow = 1,
        remap = false)
    private LootRarity apothicAscension$random(
        GenContext gen,
        Set<LootRarity> rarities,
        ObjectArrayList<ItemStack> generatedLoot,
        LootContext loot,
        GenContext methodContext
    ) {
        return NaturalItemRarityRoller.maybeAscend(LootRarity.random(gen, rarities), loot, methodContext);
    }

}
