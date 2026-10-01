// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.registry;

import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.AscensionRarity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ApothicAscension.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ASCENSION = TABS.register(
        "ascension",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.apothic_ascension.ascension"))
            .icon(() -> ModItems.ASCENSION_SEAL.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.ASCENSION_FORGE.get());
                output.accept(ModItems.GEM_RESONATOR.get());
                output.accept(ModItems.AFFIX_LOOM.get());
                output.accept(ModItems.APEX_ASCENSION_BENCH.get());
                output.accept(ModItems.ASCENSION_PYLON.get());
                output.accept(ModItems.ASCENSION_SEAL.get());
                output.accept(ModItems.ASCENSION_CODEX.get());
                output.accept(ModItems.ASCENSION_CORE.get());
                output.accept(ModItems.STABILITY_THREAD.get());
                output.accept(ModItems.EVOLUTION_CATALYST.get());
                output.accept(ModItems.GEM_ASCENSION_MATRIX.get());
                output.accept(ModItems.ATTACK_FOCUS_SIGIL.get());
                output.accept(ModItems.DEFENSE_FOCUS_SIGIL.get());
                output.accept(ModItems.AGILITY_FOCUS_SIGIL.get());
                output.accept(ModItems.SUSTAIN_FOCUS_SIGIL.get());
                output.accept(ModItems.UTILITY_FOCUS_SIGIL.get());
                for (AscensionRarity rarity : AscensionRarity.values()) {
                    output.accept(ModItems.materialFor(rarity).get());
                }
            })
            .build()
    );

    private ModCreativeTabs() {}
}
