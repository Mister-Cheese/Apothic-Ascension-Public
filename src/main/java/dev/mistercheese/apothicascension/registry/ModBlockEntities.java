// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.registry;

import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.block.entity.ApexBenchBlockEntity;
import dev.mistercheese.apothicascension.block.entity.AscensionPylonBlockEntity;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ApothicAscension.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ApexBenchBlockEntity>> APEX_ASCENSION_BENCH =
        BLOCK_ENTITIES.register(
            "apex_ascension_bench",
            () -> new BlockEntityType<>(ApexBenchBlockEntity::new, Set.of(ModBlocks.APEX_ASCENSION_BENCH.get()), null)
        );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AscensionPylonBlockEntity>> ASCENSION_PYLON =
        BLOCK_ENTITIES.register(
            "ascension_pylon",
            () -> new BlockEntityType<>(AscensionPylonBlockEntity::new, Set.of(ModBlocks.ASCENSION_PYLON.get()), null)
        );

    private ModBlockEntities() {}
}
