// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.registry;

import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.menu.AscensionWorkstationMenu;
import dev.mistercheese.apothicascension.menu.WorkstationKind;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, ApothicAscension.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<AscensionWorkstationMenu>> ASCENSION_FORGE =
        menu("ascension_forge", WorkstationKind.ASCENSION_FORGE);
    public static final DeferredHolder<MenuType<?>, MenuType<AscensionWorkstationMenu>> GEM_RESONATOR =
        menu("gem_resonator", WorkstationKind.GEM_RESONATOR);
    public static final DeferredHolder<MenuType<?>, MenuType<AscensionWorkstationMenu>> AFFIX_LOOM =
        menu("affix_loom", WorkstationKind.AFFIX_LOOM);
    public static final DeferredHolder<MenuType<?>, MenuType<AscensionWorkstationMenu>> APEX_ASCENSION_BENCH =
        menu("apex_ascension_bench", WorkstationKind.APEX_ASCENSION_BENCH);

    private static DeferredHolder<MenuType<?>, MenuType<AscensionWorkstationMenu>> menu(
        String name,
        WorkstationKind kind
    ) {
        return MENUS.register(
            name,
            () -> new MenuType<>((containerId, inventory) -> AscensionWorkstationMenu.client(
                kind,
                containerId,
                inventory
            ), FeatureFlags.DEFAULT_FLAGS)
        );
    }

    public static MenuType<AscensionWorkstationMenu> typeFor(WorkstationKind kind) {
        return switch (kind) {
            case ASCENSION_FORGE -> ASCENSION_FORGE.get();
            case GEM_RESONATOR -> GEM_RESONATOR.get();
            case AFFIX_LOOM -> AFFIX_LOOM.get();
            case APEX_ASCENSION_BENCH -> APEX_ASCENSION_BENCH.get();
        };
    }

    private ModMenus() {}
}
