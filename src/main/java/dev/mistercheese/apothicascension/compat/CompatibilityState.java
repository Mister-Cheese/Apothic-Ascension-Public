// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.compat;

/** Stable provider states exposed through diagnostics and the compatibility manifest. */
public enum CompatibilityState {
    NATIVE("native"),
    AA_FALLBACK("aa_fallback"),
    THIRD_PARTY_DOMINANT("third_party_dominant"),
    AA_EXTENSION_ONLY("aa_extension_only"),
    DISABLED("disabled"),
    DEGRADED("degraded");

    private final String id;

    CompatibilityState(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}
