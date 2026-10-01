// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.client.codex;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mistercheese.apothicascension.registry.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Client-only Codex input layer. No common/server class links Minecraft client classes to the item. */
public final class CodexClientController {
    private static final KeyMapping OPEN_CODEX = new KeyMapping(
        "key.apothic_ascension.open_codex",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_O,
        "key.categories.apothic_ascension"
    );
    private static boolean registered;

    public static void register(IEventBus modBus) {
        if (registered) return;
        registered = true;
        modBus.addListener(CodexClientController::registerKeys);
        NeoForge.EVENT_BUS.addListener(CodexClientController::clientTick);
        NeoForge.EVENT_BUS.addListener(CodexClientController::interactionInput);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CODEX);
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_CODEX.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) open();
        }
    }

    private static void interactionInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        InteractionHand hand = event.getHand();
        ItemStack held = minecraft.player.getItemInHand(hand);
        if (held.getItem() != ModItems.ASCENSION_CODEX.get()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        open();
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new AscensionCodexScreen());
    }

    private CodexClientController() {}
}
