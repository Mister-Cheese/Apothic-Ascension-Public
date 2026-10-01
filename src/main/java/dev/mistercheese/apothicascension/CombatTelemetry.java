// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import dev.mistercheese.apothicascension.registry.ModAttachments;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Bounded server-local combat telemetry for Ascension boss tuning.
 *
 * <p>State is a transient player attachment. There is no process-global map, no retained entity
 * reference and no persistence/networking cost.</p>
 *
 * <p>Pre-event values and final health loss are intentionally tracked separately. NeoForge's
 * {@code LivingDamageEvent.Pre} occurs after armor/potion reductions but before absorption, while
 * {@code LivingDamageEvent.Post} exposes the final amount lost from health.</p>
 */
@EventBusSubscriber(modid = ApothicAscension.MODID)
public final class CombatTelemetry {
    public record Snapshot(
        boolean present,
        String rarity,
        String stage,
        long elapsedTicks,
        float bossMaxHealth,
        int outgoingHits,
        double outgoingPreCap,
        double outgoingAllowed,
        double outgoingHealthLoss,
        float maxOutgoingPreCap,
        float maxOutgoingAllowed,
        float maxOutgoingHealthLoss,
        int cappedHits,
        int incomingHits,
        double incomingAllowed,
        double incomingHealthLoss,
        float maxIncomingAllowed,
        float maxIncomingHealthLoss,
        boolean bossDied,
        boolean playerDied
    ) {}

    private CombatTelemetry() {}

    /** Records the player->boss value after mitigation/EHP scaling and after the Ascension hit cap. */
    public static void recordPlayerToBossPre(
        Player player,
        LivingEntity boss,
        AscensionRarity rarity,
        float preCap,
        float allowed,
        boolean capped
    ) {
        if (!serverSide(player) || boss == null || rarity == null) return;
        CombatSession session = session(player, boss, rarity);
        session.lastTick = gameTime(player);
        session.outgoingHits++;
        session.outgoingPreCap += finitePositive(preCap);
        session.outgoingAllowed += finitePositive(allowed);
        session.maxOutgoingPreCap = Math.max(session.maxOutgoingPreCap, finitePositive(preCap));
        session.maxOutgoingAllowed = Math.max(session.maxOutgoingAllowed, finitePositive(allowed));
        if (capped) session.cappedHits++;
    }

    /** Records actual player->boss health loss from LivingDamageEvent.Post. */
    public static void recordPlayerToBossPost(Player player, LivingEntity boss, float healthLoss) {
        CombatSession session = matchingSession(player, boss);
        if (session == null) return;
        session.lastTick = gameTime(player);
        session.outgoingHealthLoss += finitePositive(healthLoss);
        session.maxOutgoingHealthLoss = Math.max(session.maxOutgoingHealthLoss, finitePositive(healthLoss));
    }

    /** Records boss->player post-mitigation damage before absorption. */
    public static void recordBossToPlayerPre(Player player, LivingEntity boss, AscensionRarity rarity, float allowed) {
        if (!serverSide(player) || boss == null || rarity == null) return;
        CombatSession session = session(player, boss, rarity);
        session.lastTick = gameTime(player);
        session.incomingHits++;
        session.incomingAllowed += finitePositive(allowed);
        session.maxIncomingAllowed = Math.max(session.maxIncomingAllowed, finitePositive(allowed));
    }

    /** Records actual boss->player health loss from LivingDamageEvent.Post. */
    public static void recordBossToPlayerPost(Player player, LivingEntity boss, float healthLoss) {
        CombatSession session = matchingSession(player, boss);
        if (session == null) return;
        session.lastTick = gameTime(player);
        session.incomingHealthLoss += finitePositive(healthLoss);
        session.maxIncomingHealthLoss = Math.max(session.maxIncomingHealthLoss, finitePositive(healthLoss));
    }

    public static Snapshot snapshot(Player player) {
        if (player == null) return empty();
        CombatSession session = player.getExistingDataOrNull(ModAttachments.COMBAT_SESSION);
        if (session == null || session.bossId == null) return empty();

        long now = gameTime(player);
        long end = session.finishTick >= 0L ? session.finishTick : Math.max(now, session.lastTick);
        return new Snapshot(
            true,
            session.rarityKey,
            session.stageKey,
            Math.max(0L, end - session.startTick),
            session.bossMaxHealth,
            session.outgoingHits,
            session.outgoingPreCap,
            session.outgoingAllowed,
            session.outgoingHealthLoss,
            session.maxOutgoingPreCap,
            session.maxOutgoingAllowed,
            session.maxOutgoingHealthLoss,
            session.cappedHits,
            session.incomingHits,
            session.incomingAllowed,
            session.incomingHealthLoss,
            session.maxIncomingAllowed,
            session.maxIncomingHealthLoss,
            session.bossDied,
            session.playerDied);
    }

    public static boolean clear(Player player) {
        return player != null && player.removeData(ModAttachments.COMBAT_SESSION) != null;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        long tick = dead.level().getGameTime();

        if (dead instanceof Player player) {
            CombatSession session = player.getExistingDataOrNull(ModAttachments.COMBAT_SESSION);
            if (session == null || session.bossId == null) return;
            Entity attacker = event.getSource().getEntity();
            if (attacker != null && session.bossId.equals(attacker.getUUID())) {
                session.playerDied = true;
                session.lastTick = tick;
                session.finishTick = tick;
            }
            return;
        }

        // Boss deaths are rare; an O(players) scan here is simpler and more lifecycle-safe than a
        // second reverse-index map that would require join/leave/restart cleanup.
        if (!(dead.level() instanceof ServerLevel level)) return;
        UUID deadId = dead.getUUID();
        for (var player : level.players()) {
            CombatSession session = player.getExistingDataOrNull(ModAttachments.COMBAT_SESSION);
            if (session != null && deadId.equals(session.bossId)) {
                session.bossDied = true;
                session.lastTick = tick;
                session.finishTick = tick;
            }
        }
    }

    private static CombatSession session(Player player, LivingEntity boss, AscensionRarity rarity) {
        CombatSession session = player.getData(ModAttachments.COMBAT_SESSION);
        UUID bossId = boss.getUUID();
        if (session.bossId == null || !session.bossId.equals(bossId)) {
            long now = gameTime(player);
            session.reset(
                bossId,
                rarity.key(),
                AscensionStageTracker.getStored(player).key(),
                now,
                boss.getMaxHealth());
        }
        return session;
    }

    private static CombatSession matchingSession(Player player, LivingEntity boss) {
        if (!serverSide(player) || boss == null) return null;
        CombatSession session = player.getExistingDataOrNull(ModAttachments.COMBAT_SESSION);
        if (session == null || session.bossId == null || !session.bossId.equals(boss.getUUID())) return null;
        return session;
    }

    private static boolean serverSide(Player player) {
        return player != null && !player.level().isClientSide();
    }

    private static long gameTime(Player player) {
        return player.level().getGameTime();
    }

    private static float finitePositive(float value) {
        return value > 0.0F && Float.isFinite(value) ? value : 0.0F;
    }

    private static Snapshot empty() {
        return new Snapshot(false, "", "", 0L, 0.0F,
            0, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, 0.0F, 0,
            0, 0.0D, 0.0D, 0.0F, 0.0F, false, false);
    }
}
