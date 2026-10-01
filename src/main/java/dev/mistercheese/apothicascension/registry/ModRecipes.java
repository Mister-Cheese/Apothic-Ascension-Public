// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.registry;

import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.recipe.GemAscensionRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, ApothicAscension.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<GemAscensionRecipe>> GEM_ASCENSION =
        SERIALIZERS.register("gem_ascension", () -> new SimpleCraftingRecipeSerializer<>(GemAscensionRecipe::new));

    private ModRecipes() {}
}
