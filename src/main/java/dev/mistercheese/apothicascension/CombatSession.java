// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import java.util.UUID;

/**
 * Server-local mutable combat diagnostic state.
 *
 * <p>This object is intentionally non-serializable and is stored as a NeoForge player attachment.
 * It therefore follows the player lifecycle without becoming save data or a process-global map.</p>
 *
 * <p>"Allowed" damage is the value present in {@code LivingDamageEvent.Pre}: normal mitigation has
 * run and Ascension pressure/caps have been applied, but absorption is still downstream. "Health
 * loss" is recorded from {@code LivingDamageEvent.Post} and is the value that actually reached the
 * target's health. Keeping both makes the telemetry useful without mislabeling pre-absorption data
 * as final damage.</p>
 */
public final class CombatSession {
    UUID bossId;
    String rarityKey = "";
    String stageKey = "";
    long startTick;
    long lastTick;
    long finishTick = -1L;
    float bossMaxHealth;

    int outgoingHits;
    double outgoingPreCap;
    double outgoingAllowed;
    double outgoingHealthLoss;
    float maxOutgoingPreCap;
    float maxOutgoingAllowed;
    float maxOutgoingHealthLoss;
    int cappedHits;

    int incomingHits;
    double incomingAllowed;
    double incomingHealthLoss;
    float maxIncomingAllowed;
    float maxIncomingHealthLoss;

    boolean bossDied;
    boolean playerDied;

    void reset(UUID newBossId, String rarity, String stage, long tick, float maxHealth) {
        this.bossId = newBossId;
        this.rarityKey = rarity;
        this.stageKey = stage;
        this.startTick = tick;
        this.lastTick = tick;
        this.finishTick = -1L;
        this.bossMaxHealth = maxHealth;

        this.outgoingHits = 0;
        this.outgoingPreCap = 0.0D;
        this.outgoingAllowed = 0.0D;
        this.outgoingHealthLoss = 0.0D;
        this.maxOutgoingPreCap = 0.0F;
        this.maxOutgoingAllowed = 0.0F;
        this.maxOutgoingHealthLoss = 0.0F;
        this.cappedHits = 0;

        this.incomingHits = 0;
        this.incomingAllowed = 0.0D;
        this.incomingHealthLoss = 0.0D;
        this.maxIncomingAllowed = 0.0F;
        this.maxIncomingHealthLoss = 0.0F;

        this.bossDied = false;
        this.playerDied = false;
    }
}
