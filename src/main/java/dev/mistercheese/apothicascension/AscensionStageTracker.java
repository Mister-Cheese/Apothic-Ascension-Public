// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import dev.mistercheese.apothicascension.config.AscensionServerConfig;
import dev.mistercheese.apothicascension.network.AscensionNetworking;
import dev.mistercheese.apothicascension.registry.ModAttachments;
import dev.mistercheese.apothicascension.registry.ModTags;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Persistent post-Pinnacle progression, backed by NeoForge player attachments. */
@EventBusSubscriber(modid = ApothicAscension.MODID)
public final class AscensionStageTracker {
    private static final String LEGACY_NBT_STAGE = "apothic_ascension.stage";
    private static final String LEGACY_NBT_DEBUG_OVERRIDE = "apothic_ascension.debug_stage_override";
    private static final AscensionStage[] STAGES = AscensionStage.values();

    private AscensionStageTracker() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        migrateLegacy(player);
        refresh(player, false);
        sync(player);
    }

    /**
     * Refresh immediately after tag/datapack synchronization so tag-backed stage classification
     * never remains stale until the next periodic inventory scan.  The same event also gives a
     * reliable owning-client synchronization point after login and global /reload.
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(player -> {
            refresh(player, false);
            AscensionNetworking.sync(player);
        });
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        // De-phase work across players instead of producing a synchronized scan spike.
        int interval = AscensionServerConfig.progressionRefreshIntervalTicks();
        if (Math.floorMod(player.level().getGameTime() + player.getId(), interval) != 0L) return;
        refresh(player, true);
    }

    public static AscensionStage resolveForLoot(LootContext loot, GenContext gen) {
        if (gen == null || gen.tier() != WorldTier.PINNACLE) return AscensionStage.LOCKED;
        Player player = loot == null ? null : GenContext.findPlayer(loot);
        return player == null ? AscensionStage.LOCKED : getStored(player);
    }

    /** Returns the effective stage, honoring a transient operator debug override if present. */
    public static AscensionStage getStored(Player player) {
        if (player == null) return AscensionStage.LOCKED;
        Integer debug = player.getExistingDataOrNull(ModAttachments.DEBUG_STAGE_OVERRIDE);
        if (debug != null && debug > 0) return AscensionStage.byId(debug - 1);
        return AscensionStage.byId(player.getData(ModAttachments.ASCENSION_STAGE));
    }

    /** Returns the persisted progression stage without applying the debug override. */
    public static AscensionStage getProgressionStored(Player player) {
        if (player == null) return AscensionStage.LOCKED;
        return AscensionStage.byId(player.getData(ModAttachments.ASCENSION_STAGE));
    }

    public static AscensionStage setDebugOverride(Player player, AscensionStage stage) {
        if (player == null || stage == null) return AscensionStage.LOCKED;
        player.setData(ModAttachments.DEBUG_STAGE_OVERRIDE, stage.id() + 1);
        sync(player);
        return stage;
    }

    public static AscensionStage clearDebugOverride(Player player) {
        if (player == null) return AscensionStage.LOCKED;
        player.removeData(ModAttachments.DEBUG_STAGE_OVERRIDE);
        AscensionStage resolved = refresh(player, false);
        sync(player);
        return resolved;
    }

    public static boolean hasDebugOverride(Player player) {
        if (player == null) return false;
        Integer debug = player.getExistingDataOrNull(ModAttachments.DEBUG_STAGE_OVERRIDE);
        return debug != null && debug > 0;
    }

    public static AscensionStage refresh(Player player, boolean announce) {
        if (player == null || player.level().isClientSide()) return AscensionStage.LOCKED;

        Integer debug = player.getExistingDataOrNull(ModAttachments.DEBUG_STAGE_OVERRIDE);
        if (debug != null && debug > 0) return AscensionStage.byId(debug - 1);

        int before = player.getData(ModAttachments.ASCENSION_STAGE);
        int next = Math.max(AscensionStage.LOCKED.id(), before);

        if (WorldTier.getTier(player) == WorldTier.PINNACLE) {
            next = Math.max(next, AscensionStage.PINNACLE_HANDOFF.id());
        }

        // Once terminal progression is reached, inventory classification can never raise it again.
        if (next >= AscensionStage.PINNACLE_HANDOFF.id() && next < AscensionStage.APOTHEOTIC.id()) {
            next = Math.max(next, materialStage(player.getInventory()));
        }

        AscensionStage resolved = AscensionStage.byId(next);
        if (resolved.id() != before) {
            player.setData(ModAttachments.ASCENSION_STAGE, resolved.id());
            sync(player);
            if (announce && AscensionServerConfig.announceStageUnlocks() && resolved != AscensionStage.LOCKED) {
                player.sendSystemMessage(Component.translatable(
                    "message.apothic_ascension.stage_unlocked",
                    Component.translatable("stage.apothic_ascension." + resolved.key())));
            }
        }
        return resolved;
    }

    private static int materialStage(Inventory inventory) {
        if (inventory == null) return AscensionStage.LOCKED.id();
        int stage = AscensionStage.LOCKED.id();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.is(ModTags.APOTHEOTIC_STAGE_MATERIALS)) return AscensionStage.APOTHEOTIC.id();
            if (stack.is(ModTags.TRANSCENDENT_STAGE_MATERIALS)) stage = Math.max(stage, AscensionStage.TRANSCENDENT.id());
            else if (stack.is(ModTags.ESOTERIC_STAGE_MATERIALS)) stage = Math.max(stage, AscensionStage.ESOTERIC.id());
            else if (stack.is(ModTags.CELESTIAL_STAGE_MATERIALS)) stage = Math.max(stage, AscensionStage.CELESTIAL.id());
        }
        return stage;
    }

    private static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            AscensionNetworking.sync(serverPlayer);
        }
    }

    /** One-way migration performed once at server login from Alpha 8.1 raw persistent NBT keys. */
    private static void migrateLegacy(Player player) {
        CompoundTag legacy = player.getPersistentData();
        if (!player.hasData(ModAttachments.ASCENSION_STAGE) && legacy.contains(LEGACY_NBT_STAGE, Tag.TAG_INT)) {
            player.setData(ModAttachments.ASCENSION_STAGE, AscensionStage.byId(legacy.getInt(LEGACY_NBT_STAGE)).id());
        }
        legacy.remove(LEGACY_NBT_STAGE);
        legacy.remove(LEGACY_NBT_DEBUG_OVERRIDE); // debug state intentionally does not survive migration/restart
    }

}
