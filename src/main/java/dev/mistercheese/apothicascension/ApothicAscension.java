// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import dev.mistercheese.apothicascension.client.ClientRegistration;
import dev.mistercheese.apothicascension.compat.CompatibilityManager;
import dev.mistercheese.apothicascension.config.AscensionClientConfig;
import dev.mistercheese.apothicascension.config.AscensionServerConfig;
import dev.mistercheese.apothicascension.network.AscensionNetworking;
import dev.mistercheese.apothicascension.registry.ModAttachments;
import dev.mistercheese.apothicascension.registry.ModBlockEntities;
import dev.mistercheese.apothicascension.registry.ModBlocks;
import dev.mistercheese.apothicascension.registry.ModCreativeTabs;
import dev.mistercheese.apothicascension.registry.ModDataComponents;
import dev.mistercheese.apothicascension.registry.ModItems;
import dev.mistercheese.apothicascension.registry.ModMenus;
import dev.mistercheese.apothicascension.registry.ModRecipes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;

@Mod(ApothicAscension.MODID)
public final class ApothicAscension {
    public static final String MODID = "apothic_ascension";
    public static final String BUILD_ID = "1.14.2-Beta";

    public ApothicAscension(IEventBus modBus, ModContainer modContainer) {
        CompatibilityManager.initialize();
        ModDataComponents.DATA_COMPONENTS.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        AscensionServerConfig.register(modContainer);
        AscensionClientConfig.register(modContainer);
        AscensionNetworking.register(modBus);

        // Client-only registration stays behind the physical-client distribution check so the
        // dedicated-server class path never needs to resolve client implementation classes.
        if (FMLLoader.getDist().isClient()) {
            ClientRegistration.register(modBus, modContainer);
        }
    }
}
