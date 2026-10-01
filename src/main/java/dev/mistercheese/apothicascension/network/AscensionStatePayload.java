// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.network;

import dev.mistercheese.apothicascension.ApothicAscension;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Owning-client display copy of server-authoritative Ascension progression state. */
public record AscensionStatePayload(int progressionStage, int debugOverride) implements CustomPacketPayload {
    public static final Type<AscensionStatePayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ApothicAscension.MODID, "ascension_state"));

    public static final StreamCodec<ByteBuf, AscensionStatePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        AscensionStatePayload::progressionStage,
        ByteBufCodecs.VAR_INT,
        AscensionStatePayload::debugOverride,
        AscensionStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
