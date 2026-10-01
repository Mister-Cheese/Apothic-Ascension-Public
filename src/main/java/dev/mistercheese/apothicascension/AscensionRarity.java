// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Ascension rarities above Apotheosis Mythic.
 *
 * <p>The affix/effect scales are the stabilized pre-Beta item-power curve. Boss pressure is a
 * deliberately separate curve used only for Apotheosis invaders carrying the matching rarity
 * marker. Keeping these curves separate avoids silently feeding boss-balance changes back into
 * generated item affixes.</p>
 *
 * <p>Rank and sort lookups are immutable array lookups because they are used from damage-time
 * classification. Rarity ids use a precomputed immutable map. This is intentionally simple static
 * data, not a runtime cache.</p>
 */
public enum AscensionRarity {
    LEGENDARY(6, "legendary", 800, 1.22, 1.18, 1.20, 1.05, 0.75),
    ANCIENT(7, "ancient", 900, 1.44, 1.30, 1.35, 1.10, 0.65),
    FORGOTTEN(8, "forgotten", 1000, 1.72, 1.45, 1.55, 1.15, 0.55),
    PRIMAL(9, "primal", 1100, 2.08, 1.65, 1.85, 1.22, 0.45),
    STELLAR(10, "stellar", 1200, 2.58, 1.90, 2.20, 1.30, 0.38),
    DIVINE(11, "divine", 1300, 3.30, 2.20, 2.65, 1.42, 0.33),
    ESOTERIC(12, "esoteric", 1400, 4.35, 2.60, 3.20, 1.56, 0.29),
    CATACLYSMIC(13, "cataclysmic", 1500, 5.90, 3.15, 3.85, 1.73, 0.25),
    ABYSSAL(14, "abyssal", 1600, 8.25, 3.95, 4.65, 1.93, 0.22),
    EMPYREAN(15, "empyrean", 1700, 11.90, 5.10, 5.60, 2.17, 0.19),
    PARACAUSAL(16, "paracausal", 1800, 17.80, 6.85, 6.70, 2.43, 0.16),
    TRANSCENDENT(17, "transcendent", 1900, 27.50, 9.60, 7.95, 2.69, 0.14),
    APOTHEOTIC(18, "apotheotic", 2000, 42.00, 13.50, 9.25, 2.95, 0.125);

    private static final int FIRST_SORT = LEGENDARY.sort;
    private static final int SORT_STRIDE = 100;
    private static final AscensionRarity[] BY_RANK = new AscensionRarity[APOTHEOTIC.rank + 1];
    private static final AscensionRarity[] BY_SORT = new AscensionRarity[(APOTHEOTIC.sort - FIRST_SORT) / SORT_STRIDE + 1];
    private static final Map<String, AscensionRarity> BY_KEY;

    static {
        Map<String, AscensionRarity> byKey = new HashMap<>();
        for (AscensionRarity rarity : values()) {
            if (rarity.rank < 0 || rarity.rank >= BY_RANK.length || BY_RANK[rarity.rank] != null) {
                throw new ExceptionInInitializerError("AscensionRarity ranks must be unique");
            }
            BY_RANK[rarity.rank] = rarity;

            int delta = rarity.sort - FIRST_SORT;
            if (delta < 0 || delta % SORT_STRIDE != 0) {
                throw new ExceptionInInitializerError("AscensionRarity sort indices must use a 100-point stride");
            }
            int slot = delta / SORT_STRIDE;
            if (slot >= BY_SORT.length || BY_SORT[slot] != null) {
                throw new ExceptionInInitializerError("AscensionRarity sort indices must be unique");
            }
            BY_SORT[slot] = rarity;
            byKey.put(rarity.key, rarity);
        }
        for (AscensionRarity rarity : BY_SORT) {
            if (rarity == null) throw new ExceptionInInitializerError("AscensionRarity sort indices must be contiguous");
        }
        BY_KEY = Map.copyOf(byKey);
    }

    private final int rank;
    private final String key;
    private final int sort;
    private final double affixScale;
    private final double effectScale;
    private final double bossEffectiveHealthMultiplier;
    private final double bossDamageMultiplier;
    private final double bossSingleHitCapFraction;

    AscensionRarity(
        int rank,
        String key,
        int sort,
        double affixScale,
        double effectScale,
        double bossEffectiveHealthMultiplier,
        double bossDamageMultiplier,
        double bossSingleHitCapFraction
    ) {
        this.rank = rank;
        this.key = key;
        this.sort = sort;
        this.affixScale = affixScale;
        this.effectScale = effectScale;
        this.bossEffectiveHealthMultiplier = bossEffectiveHealthMultiplier;
        this.bossDamageMultiplier = bossDamageMultiplier;
        this.bossSingleHitCapFraction = bossSingleHitCapFraction;
    }

    public int rank() { return rank; }
    public String key() { return key; }
    public int sortIndex() { return sort; }
    public double affixScale() { return affixScale; }
    public double effectScale() { return effectScale; }
    public double bossEffectiveHealthMultiplier() { return bossEffectiveHealthMultiplier; }
    public double bossDamageMultiplier() { return bossDamageMultiplier; }
    public double bossSingleHitCapFraction() { return bossSingleHitCapFraction; }
    public int postMythicIndex() { return rank - 5; }
    public double armorMultiplier() { return Math.pow(1.44D, postMythicIndex()); }
    public double attackMultiplier() { return Math.pow(1.30D, postMythicIndex()); }
    public double toughnessMultiplier() { return Math.pow(1.30D, postMythicIndex()); }
    public String id() { return ApothicAscension.MODID + ":" + key; }

    public static AscensionRarity byRank(int rank) {
        return rank >= 0 && rank < BY_RANK.length ? BY_RANK[rank] : null;
    }

    public static AscensionRarity bySortIndex(int sort) {
        int delta = sort - FIRST_SORT;
        if (delta < 0 || delta % SORT_STRIDE != 0) return null;
        int slot = delta / SORT_STRIDE;
        return slot < BY_SORT.length ? BY_SORT[slot] : null;
    }

    /** Accepts both namespaced rarity ids and bare Ascension keys. */
    public static AscensionRarity byId(String id) {
        if (id == null || id.isBlank()) return null;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        String prefix = ApothicAscension.MODID + ":";
        if (normalized.startsWith(prefix)) normalized = normalized.substring(prefix.length());
        return BY_KEY.get(normalized);
    }
}
