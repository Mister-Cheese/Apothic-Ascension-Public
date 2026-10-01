// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/**
 * Persistent post-Pinnacle progression bands.
 *
 * <p>The small id lookup table is intentionally precomputed because stage reads occur in combat
 * paths. It replaces repeated {@code values()} array creation/iteration without adding mutable
 * cache state or invalidation rules.</p>
 */
public enum AscensionStage {
    LOCKED(0, "locked", 700, 1.00D, 1.00D),
    PINNACLE_HANDOFF(1, "ascendant", 1000, 1.35D, 1.40D),
    CELESTIAL(2, "celestial", 1300, 1.85D, 2.00D),
    ESOTERIC(3, "esoteric", 1500, 2.65D, 3.00D),
    TRANSCENDENT(4, "transcendent", 1900, 3.85D, 4.60D),
    APOTHEOTIC(5, "apotheotic", 2000, 5.50D, 7.00D);

    private static final AscensionStage[] BY_ID = new AscensionStage[APOTHEOTIC.id + 1];
    private static final Map<String, AscensionStage> BY_KEY;

    static {
        Map<String, AscensionStage> byKey = new HashMap<>();
        for (AscensionStage stage : values()) {
            if (stage.id < 0 || stage.id >= BY_ID.length || BY_ID[stage.id] != null) {
                throw new ExceptionInInitializerError("AscensionStage ids must be unique and contiguous");
            }
            BY_ID[stage.id] = stage;
            byKey.put(stage.key, stage);
            byKey.put(stage.name().toLowerCase(Locale.ROOT), stage);
        }
        for (int i = 0; i < BY_ID.length; i++) {
            if (BY_ID[i] == null) throw new ExceptionInInitializerError("Missing AscensionStage id " + i);
        }
        BY_KEY = Map.copyOf(byKey);
    }

    private final int id;
    private final String key;
    private final int naturalSortCap;
    private final double hostileDamageMultiplier;
    private final double hostileEffectiveHealthMultiplier;

    AscensionStage(int id, String key, int naturalSortCap, double hostileDamageMultiplier, double hostileEffectiveHealthMultiplier) {
        this.id = id;
        this.key = key;
        this.naturalSortCap = naturalSortCap;
        this.hostileDamageMultiplier = hostileDamageMultiplier;
        this.hostileEffectiveHealthMultiplier = hostileEffectiveHealthMultiplier;
    }

    public int id() { return id; }
    public String key() { return key; }
    public int naturalSortCap() { return naturalSortCap; }
    public double hostileDamageMultiplier() { return hostileDamageMultiplier; }
    public double hostileEffectiveHealthMultiplier() { return hostileEffectiveHealthMultiplier; }

    /** Clamps legacy/corrupt integer values to the nearest representable progression boundary. */
    public static AscensionStage byId(int id) {
        if (id <= LOCKED.id) return LOCKED;
        if (id >= APOTHEOTIC.id) return APOTHEOTIC;
        return BY_ID[id];
    }

    public static AscensionStage byKey(String key) {
        if (key == null || key.isBlank()) return null;
        return BY_KEY.get(key.trim().toLowerCase(Locale.ROOT));
    }
}
