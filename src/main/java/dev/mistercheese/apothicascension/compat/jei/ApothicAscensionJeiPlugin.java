// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.compat.jei;

import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.GemTier;
import dev.mistercheese.apothicascension.GemTierData;
import dev.shadowsoffire.apotheosis.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.socket.gem.GemRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** JEI integration for the virtual gem tiers above Apotheosis Perfect. */
@JeiPlugin
public final class ApothicAscensionJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ApothicAscension.MODID, "gem_tiers");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        List<ItemStack> stacks = createExtendedGemStacks();
        if (!stacks.isEmpty()) {
            registration.addExtraItemStacks(stacks);
        }
    }

    private static List<ItemStack> createExtendedGemStacks() {
        Collection<Gem> gems = GemRegistry.INSTANCE.getValues();
        if (gems == null || gems.isEmpty()) return List.of();

        List<ItemStack> stacks = new ArrayList<>(gems.size() * GemTier.APOTHEOTIC.id());
        for (Gem gem : gems) {
            // Extended tiers are binary-compatible Perfect gems carrying our custom tier marker.
            // JEI subtype identity is supplied by JeiGemSubtypeMixin, which augments Apotheosis's
            // own gem-id + purity subtype key instead of competing with it.
            for (GemTier tier : GemTier.values()) {
                if (tier == GemTier.PERFECT) continue;
                ItemStack stack = gem.toStack(Purity.PERFECT);
                if (stack == null || stack.isEmpty()) continue;
                GemTierData.set(stack, tier.id());
                stacks.add(stack);
            }
        }
        return stacks;
    }
}
