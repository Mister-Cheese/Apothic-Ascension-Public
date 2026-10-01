// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.recipe;

import dev.mistercheese.apothicascension.GemTier;
import dev.mistercheese.apothicascension.GemTierData;
import dev.mistercheese.apothicascension.registry.ModItems;
import dev.mistercheese.apothicascension.registry.ModRecipes;
import dev.mistercheese.apothicascension.registry.ModTags;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Dynamic gem-grade upgrade recipe.
 *
 * The output is a copy of the exact input gem, so the underlying gem identity and all data
 * components survive. This is intentionally a normal crafting recipe: Create 6's Mechanical
 * Crafters query RecipeType.CRAFTING before their own mechanical-crafting recipe type, allowing
 * this recipe to be automated without linking against Create classes.
 */
public final class GemAscensionRecipe extends CustomRecipe {
    public GemAscensionRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findMatch(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Match match = findMatch(input);
        if (match == null) return ItemStack.EMPTY;

        ItemStack output = match.gem().copy();
        output.setCount(1);
        GemTierData.set(output, match.next().id());
        return output;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack != null && !stack.isEmpty() && stack.getItem() == ModItems.GEM_ASCENSION_MATRIX.get()) {
                ItemStack matrix = stack.copy();
                matrix.setCount(1);
                remaining.set(i, matrix);
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.GEM_ASCENSION.get();
    }

    private static Match findMatch(CraftingInput input) {
        if (input == null) return null;
        ItemStack gem = ItemStack.EMPTY;
        ItemStack material = ItemStack.EMPTY;
        boolean matrix = false;
        int occupied = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            occupied++;

            if (stack.getItem() instanceof GemItem) {
                if (gem != ItemStack.EMPTY && !gem.isEmpty()) return null;
                gem = stack;
            }
            else if (stack.getItem() == ModItems.GEM_ASCENSION_MATRIX.get()) {
                if (matrix) return null;
                matrix = true;
            }
            else {
                if (material != ItemStack.EMPTY && !material.isEmpty()) return null;
                material = stack;
            }
        }

        if (occupied != 3 || gem == ItemStack.EMPTY || gem.isEmpty() || !matrix || material == ItemStack.EMPTY || material.isEmpty()) return null;
        if (GemItem.getPurity(gem) != Purity.PERFECT) return null;

        int current = GemTierData.get(gem);
        if (current >= GemTier.APOTHEOTIC.id()) return null;
        GemTier next = GemTier.byId(current).next();
        if (next == GemTier.TRANSCENDENT || next == GemTier.APOTHEOTIC) return null;
        if (!material.is(ModTags.gemUpgradeMaterial(next))) return null;
        return new Match(gem, next);
    }

    private record Match(ItemStack gem, GemTier next) {}
}
