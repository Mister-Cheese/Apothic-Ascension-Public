// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.registry;

import com.mojang.serialization.Codec;
import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.GemTier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** First-party item state owned by Apothic Ascension. */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ApothicAscension.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GEM_GRADE =
        DATA_COMPONENTS.registerComponentType("gem_grade", builder -> builder
            .persistent(Codec.intRange(GemTier.PERFECT.id(), GemTier.APOTHEOTIC.id()))
            .networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModDataComponents() {}
}
