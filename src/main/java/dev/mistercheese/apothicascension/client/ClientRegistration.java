// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.client;

import dev.mistercheese.apothicascension.client.codex.CodexClientController;

import dev.mistercheese.apothicascension.client.renderer.ApexSealRenderer;
import dev.mistercheese.apothicascension.client.renderer.PylonRingRenderer;
import dev.mistercheese.apothicascension.client.renderer.RarityItemCircleRenderer;
import dev.mistercheese.apothicascension.client.screen.AscensionWorkstationScreen;
import dev.mistercheese.apothicascension.registry.ModBlockEntities;
import dev.mistercheese.apothicascension.registry.ModMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientRegistration {
    public static void register(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        CodexClientController.register(modBus);
        modBus.addListener(ClientRegistration::registerScreens);
        modBus.addListener(ClientRegistration::registerRenderers);
        NeoForge.EVENT_BUS.addListener(RarityItemCircleRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(RarityItemCircleRenderer::onRenderLevel);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ASCENSION_FORGE.get(), AscensionWorkstationScreen::new);
        event.register(ModMenus.GEM_RESONATOR.get(), AscensionWorkstationScreen::new);
        event.register(ModMenus.AFFIX_LOOM.get(), AscensionWorkstationScreen::new);
        event.register(ModMenus.APEX_ASCENSION_BENCH.get(), AscensionWorkstationScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.APEX_ASCENSION_BENCH.get(), ApexSealRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ASCENSION_PYLON.get(), PylonRingRenderer::new);
    }

    private ClientRegistration() {}
}
