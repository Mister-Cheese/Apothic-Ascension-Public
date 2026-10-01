// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import dev.mistercheese.apothicascension.config.AscensionServerConfig;
import dev.mistercheese.apothicascension.registry.ModAttachments;
import dev.shadowsoffire.apotheosis.mobs.types.Invader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Per-player post-Pinnacle combat pressure.
 *
 * <p>Scaling is evaluated at damage time rather than permanently mutating mob attributes. That
 * keeps the system reversible and lets differently progressed players fight the same entity
 * without sharing a permanently inflated health/damage state.</p>
 *
 * <p>Apotheosis writes {@code apoth.boss} and {@code apoth.boss.rarity} into invader persistent
 * data. Ascension boss pressure is applied only when that marker resolves to one of our rarities.
 * Hostile outgoing pressure is applied in the incoming phase so armor and other mitigation still
 * counter it normally. Stage/rarity effective-health scaling and the anti-burst ceiling are applied
 * in {@link LivingDamageEvent.Pre}, after normal mitigation has calculated the damage that would
 * reach the target. Telemetry records actual health loss separately in {@link LivingDamageEvent.Post}.</p>
 */
@EventBusSubscriber(modid = ApothicAscension.MODID)
public final class AscensionCombatHandler {
    private AscensionCombatHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!AscensionServerConfig.combatPressure()) return;
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        float amount = event.getAmount();
        if (!(amount > 0.0F) || !Float.isFinite(amount)) return;

        // Living attacker -> player. Generic stage pressure applies to hostile Enemy instances;
        // Ascension boss pressure additionally follows Apotheosis' explicit invader marker.
        if (target instanceof Player player && attacker instanceof LivingEntity livingAttacker) {
            AscensionStage stage = AscensionStageTracker.getStored(player);
            if (stage == AscensionStage.LOCKED) return;

            double multiplier = 1.0D;
            if (livingAttacker instanceof Enemy) multiplier *= stage.hostileDamageMultiplier();
            AscensionRarity bossRarity = bossRarity(livingAttacker);
            if (bossRarity != null) multiplier *= bossRarity.bossDamageMultiplier();

            if (multiplier > 1.0D) event.setAmount(safeFloat(amount * multiplier));
        }
    }

    /**
     * Applies player-side effective-health pressure and, for Ascension bosses, the anti-burst cap.
     *
     * <p>This intentionally runs in LivingDamageEvent.Pre rather than LivingIncomingDamageEvent so
     * an "effective health multiplier" is a true post-mitigation divisor instead of a pre-armor
     * approximation. Absorption is downstream, so telemetry here is explicitly "allowed" damage,
     * not final health loss.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!AscensionServerConfig.combatPressure()) return;
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        float damage = event.getNewDamage();
        if (!(damage > 0.0F) || !Float.isFinite(damage)) return;

        if (target instanceof Player player && attacker instanceof LivingEntity livingAttacker) {
            if (AscensionStageTracker.getStored(player) == AscensionStage.LOCKED) return;
            AscensionRarity incomingRarity = bossRarity(livingAttacker);
            if (incomingRarity != null) {
                CombatTelemetry.recordBossToPlayerPre(player, livingAttacker, incomingRarity, damage);
            }
            return;
        }

        if (!(attacker instanceof Player player) || target == player) return;
        AscensionStage stage = AscensionStageTracker.getStored(player);
        if (stage == AscensionStage.LOCKED) return;

        AscensionRarity bossRarity = bossRarity(target);
        double divisor = 1.0D;
        if (target instanceof Enemy) divisor *= stage.hostileEffectiveHealthMultiplier();
        if (bossRarity != null) divisor *= bossRarity.bossEffectiveHealthMultiplier();

        float pressuredDamage = divisor > 1.0D ? safeFloat(damage / divisor) : damage;
        float allowed = pressuredDamage;
        boolean capped = false;

        if (bossRarity != null) {
            double maxHealth = target.getMaxHealth();
            if (maxHealth > 0.0D && Double.isFinite(maxHealth)) {
                double cap = maxHealth * bossRarity.bossSingleHitCapFraction();
                if (cap > 0.0D && Double.isFinite(cap) && allowed > cap) {
                    allowed = safeFloat(cap);
                    capped = true;
                }
            }
        }

        if (allowed != damage) event.setNewDamage(allowed);
        if (bossRarity != null) {
            CombatTelemetry.recordPlayerToBossPre(player, target, bossRarity, pressuredDamage, allowed, capped);
        }
    }

    /** Records the final amount that actually reached health after absorption and downstream hooks. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (!AscensionServerConfig.combatPressure()) return;
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        float healthLoss = event.getNewDamage();
        if (!(healthLoss > 0.0F) || !Float.isFinite(healthLoss)) return;

        if (target instanceof Player player && attacker instanceof LivingEntity livingAttacker) {
            if (bossRarity(livingAttacker) != null) {
                CombatTelemetry.recordBossToPlayerPost(player, livingAttacker, healthLoss);
            }
            return;
        }

        if (attacker instanceof Player player && target != player && bossRarity(target) != null) {
            CombatTelemetry.recordPlayerToBossPost(player, target, healthLoss);
        }
    }

    static AscensionRarity bossRarity(LivingEntity entity) {
        if (entity == null) return null;

        Integer cached = entity.getExistingDataOrNull(ModAttachments.BOSS_RARITY_CACHE);
        if (cached != null && cached != Integer.MIN_VALUE) {
            return cached < 0 ? null : AscensionRarity.byRank(cached);
        }

        CompoundTag data = entity.getPersistentData();
        if (!data.getBoolean(Invader.BOSS_KEY)) {
            // Do not cache a negative before Apotheosis (or an explicit integration) has finalized
            // boss initialization. Spawn/event ordering can legitimately query combat state first.
            return null;
        }

        String rarityId = data.getString(Invader.RARITY_KEY);
        if (rarityId == null || rarityId.isBlank()) {
            // The boss marker may become visible one write before its rarity marker. Keep this
            // unresolved so a later damage event can observe the completed state.
            return null;
        }

        AscensionRarity resolved = AscensionRarity.byId(rarityId);
        // Once a boss marker and non-empty rarity id both exist, a foreign rarity is a stable
        // negative classification. Native Ascension rarities cache their immutable rank.
        entity.setData(ModAttachments.BOSS_RARITY_CACHE, resolved == null ? -1 : resolved.rank());
        return resolved;
    }

    private static float safeFloat(double value) {
        if (!(value > 0.0D) || Double.isNaN(value)) return 0.0F;
        if (value >= Float.MAX_VALUE) return Float.MAX_VALUE;
        return (float) value;
    }
}
