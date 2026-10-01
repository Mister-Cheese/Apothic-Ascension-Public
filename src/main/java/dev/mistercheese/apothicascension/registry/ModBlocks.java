// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.registry;

import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.block.AscensionWorkstationBlock;
import dev.mistercheese.apothicascension.block.ApexAscensionBenchBlock;
import dev.mistercheese.apothicascension.block.AscensionPylonBlock;
import dev.mistercheese.apothicascension.menu.WorkstationKind;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ApothicAscension.MODID);

    public static final DeferredBlock<AscensionWorkstationBlock> ASCENSION_FORGE = BLOCKS.registerBlock(
        "ascension_forge",
        props -> new AscensionWorkstationBlock(props, WorkstationKind.ASCENSION_FORGE),
        workstationProperties()
    );
    public static final DeferredBlock<AscensionWorkstationBlock> GEM_RESONATOR = BLOCKS.registerBlock(
        "gem_resonator",
        props -> new AscensionWorkstationBlock(props, WorkstationKind.GEM_RESONATOR),
        workstationProperties()
    );
    public static final DeferredBlock<AscensionWorkstationBlock> AFFIX_LOOM = BLOCKS.registerBlock(
        "affix_loom",
        props -> new AscensionWorkstationBlock(props, WorkstationKind.AFFIX_LOOM),
        workstationProperties()
    );
    public static final DeferredBlock<ApexAscensionBenchBlock> APEX_ASCENSION_BENCH = BLOCKS.registerBlock(
        "apex_ascension_bench",
        ApexAscensionBenchBlock::new,
        workstationProperties().lightLevel(state -> 8)
    );
    public static final DeferredBlock<AscensionPylonBlock> ASCENSION_PYLON = BLOCKS.registerBlock(
        "ascension_pylon",
        AscensionPylonBlock::new,
        workstationProperties().lightLevel(state -> 10).noOcclusion()
    );

    private static BlockBehaviour.Properties workstationProperties() {
        return BlockBehaviour.Properties.of()
            .strength(5.0F, 9.0F)
            .requiresCorrectToolForDrops()
            .sound(SoundType.DEEPSLATE)
            .lightLevel(state -> 4);
    }

    public static Block blockFor(WorkstationKind kind) {
        return switch (kind) {
            case ASCENSION_FORGE -> ASCENSION_FORGE.get();
            case GEM_RESONATOR -> GEM_RESONATOR.get();
            case AFFIX_LOOM -> AFFIX_LOOM.get();
            case APEX_ASCENSION_BENCH -> APEX_ASCENSION_BENCH.get();
        };
    }

    private ModBlocks() {}
}
