// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.compat;

/** Version-audit state for an installed specialist compatibility provider. */
public enum ProviderAuditState {
    ABSENT("absent"),
    AUDITED("audited"),
    DEGRADED("degraded");

    private final String id;

    ProviderAuditState(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}
