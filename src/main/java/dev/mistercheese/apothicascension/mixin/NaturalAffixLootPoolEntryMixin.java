// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.mixin;

import dev.mistercheese.apothicascension.NaturalItemRarityRoller;
import dev.shadowsoffire.apotheosis.loot.AffixLootEntry;
import dev.shadowsoffire.apotheosis.loot.LootController;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.entry.AffixLootPoolEntry;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Adds post-Mythic rarity after the context-poor upstream pool helper returns. */
@Mixin(value = AffixLootPoolEntry.class, remap = false)
public abstract class NaturalAffixLootPoolEntryMixin {
    @Redirect(
        method = "createItemStack",
        at = @At(value = "INVOKE", target = "Ldev/shadowsoffire/apotheosis/loot/LootController;createAffixItemFromPools(Ljava/util/Set;Ljava/util/Set;Ldev/shadowsoffire/apotheosis/tiers/GenContext;)Lnet/minecraft/world/item/ItemStack;"),
        require = 1,
        allow = 1,
        remap = false)
    private ItemStack apothicAscension$ascendGenerated(
        Set<DynamicHolder<LootRarity>> rarities,
        Set<DynamicHolder<AffixLootEntry>> entries,
        GenContext gen,
        Consumer<ItemStack> output,
        LootContext loot,
        GenContext methodContext
    ) {
        ItemStack generated = LootController.createAffixItemFromPools(rarities, entries, gen);
        return NaturalItemRarityRoller.maybeAscendGenerated(generated, loot, methodContext);
    }
}
