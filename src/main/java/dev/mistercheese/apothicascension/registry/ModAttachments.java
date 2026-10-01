// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.registry;

import com.mojang.serialization.Codec;
import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.AscensionStage;
import dev.mistercheese.apothicascension.CombatSession;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Player-owned state with lifecycle semantics delegated to NeoForge. */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ApothicAscension.MODID);

    public static final Supplier<AttachmentType<Integer>> ASCENSION_STAGE = ATTACHMENTS.register(
        "ascension_stage",
        () -> AttachmentType.builder(() -> AscensionStage.LOCKED.id())
            .serialize(
                Codec.intRange(AscensionStage.LOCKED.id(), AscensionStage.APOTHEOTIC.id()),
                value -> value != null && value > AscensionStage.LOCKED.id())
            .copyOnDeath()
            .build());

    /** Operator-only transient override. It is intentionally neither persisted nor copied. */
    public static final Supplier<AttachmentType<Integer>> DEBUG_STAGE_OVERRIDE = ATTACHMENTS.register(
        "debug_stage_override",
        () -> AttachmentType.builder(() -> 0).build());


    /**
     * Transient cache for Apotheosis invader rarity classification on living entities.
     * Integer.MIN_VALUE means unresolved; -1 means a finalized boss marker resolved as foreign/non-Ascension;
     * otherwise the value is an AscensionRarity rank. Pre-initialization negative lookups are never cached,
     * so event ordering cannot permanently hide a boss rarity that is attached later in the spawn path.
     * The cache is intentionally reconstructed after entity load.
     */
    public static final Supplier<AttachmentType<Integer>> BOSS_RARITY_CACHE = ATTACHMENTS.register(
        "boss_rarity_cache",
        () -> AttachmentType.builder(() -> Integer.MIN_VALUE).build());

    /** Transient runtime diagnostics. It is intentionally neither persisted nor copied. */
    public static final Supplier<AttachmentType<CombatSession>> COMBAT_SESSION = ATTACHMENTS.register(
        "combat_session",
        () -> AttachmentType.builder(CombatSession::new).build());

    private ModAttachments() {}
}
