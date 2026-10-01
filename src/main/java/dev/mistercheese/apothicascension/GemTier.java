// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

public enum GemTier {
    PERFECT(0, "perfect", 0xED7014, 700),
    LEGENDARY(1, "legendary", 0xFFDE91, 800),
    ANCIENT(2, "ancient", 0xC33A3A, 900),
    FORGOTTEN(3, "forgotten", 0x8A5A44, 1000),
    PRIMAL(4, "primal", 0xE85DFF, 1100),
    STELLAR(5, "stellar", 0x4DA6FF, 1200),
    DIVINE(6, "divine", 0xFFF2A8, 1300),
    ESOTERIC(7, "esoteric", 0x30C7B5, 1400),
    CATACLYSMIC(8, "cataclysmic", 0xFF4FD8, 1500),
    ABYSSAL(9, "abyssal", 0x7040A8, 1600),
    EMPYREAN(10, "empyrean", 0xFFD6FF, 1700),
    PARACAUSAL(11, "paracausal", 0x66FFCC, 1800),
    TRANSCENDENT(12, "transcendent", 0xD8FFFF, 1900),
    APOTHEOTIC(13, "apotheotic", 0xFFF6CC, 2000);

    private static final GemTier[] VALUES = values();

    static {
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i].id != i) {
                throw new ExceptionInInitializerError("GemTier ids must remain contiguous and match declaration order");
            }
        }
    }

    private final int id;
    private final String key;
    private final int color;
    private final int sortIndex;

    GemTier(int id, String key, int color, int sortIndex) {
        this.id = id;
        this.key = key;
        this.color = color;
        this.sortIndex = sortIndex;
    }

    public int id() { return id; }
    public String key() { return key; }
    public int color() { return color; }
    public int sortIndex() { return sortIndex; }

    /** Existing progression curve; semantic adapters apply domain-specific safety envelopes. */
    public double multiplier() {
        return id <= 0 ? 1.0D : Math.pow(2.0D, id / 2.5D);
    }

    public GemTier next() {
        return this == APOTHEOTIC ? this : VALUES[ordinal() + 1];
    }

    public static GemTier byId(int id) {
        if (id <= 0) return PERFECT;
        if (id >= APOTHEOTIC.id) return APOTHEOTIC;
        return VALUES[id];
    }
}
