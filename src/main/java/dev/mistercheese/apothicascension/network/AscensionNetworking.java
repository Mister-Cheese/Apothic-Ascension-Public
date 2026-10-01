// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.network;

import dev.mistercheese.apothicascension.AscensionStage;
import dev.mistercheese.apothicascension.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Minimal play-phase networking for server-authoritative progression display state. */
public final class AscensionNetworking {
    private static final String PROTOCOL_VERSION = "1";

    public static void register(IEventBus modBus) {
        modBus.addListener(AscensionNetworking::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION).playToClient(
            AscensionStatePayload.TYPE,
            AscensionStatePayload.STREAM_CODEC.cast(),
            AscensionNetworking::handleClientState
        );
    }

    public static void sync(ServerPlayer player) {
        if (player == null) return;
        int progression = AscensionStage.byId(player.getData(ModAttachments.ASCENSION_STAGE)).id();
        Integer debug = player.getExistingDataOrNull(ModAttachments.DEBUG_STAGE_OVERRIDE);
        int encodedDebug = debug == null ? 0 : Math.max(0, debug);
        PacketDistributor.sendToPlayer(player, new AscensionStatePayload(progression, encodedDebug));
    }

    private static void handleClientState(AscensionStatePayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player == null) return;

        int progression = AscensionStage.byId(payload.progressionStage()).id();
        player.setData(ModAttachments.ASCENSION_STAGE, progression);

        int debug = payload.debugOverride();
        if (debug > 0) {
            AscensionStage resolved = AscensionStage.byId(debug - 1);
            player.setData(ModAttachments.DEBUG_STAGE_OVERRIDE, resolved.id() + 1);
        }
        else {
            player.removeData(ModAttachments.DEBUG_STAGE_OVERRIDE);
        }
    }

    private AscensionNetworking() {}
}
