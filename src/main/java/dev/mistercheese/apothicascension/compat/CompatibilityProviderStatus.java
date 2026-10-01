// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.compat;

import java.util.Objects;

/** Immutable runtime audit status for one specialist compatibility provider. */
public record CompatibilityProviderStatus(
    String providerModId,
    String displayName,
    ProviderAuditState state,
    String version,
    String role,
    String detail
) {
    public CompatibilityProviderStatus {
        providerModId = Objects.requireNonNull(providerModId, "providerModId");
        displayName = Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(state, "state");
        version = Objects.requireNonNull(version, "version");
        role = Objects.requireNonNull(role, "role");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean loaded() {
        return this.state != ProviderAuditState.ABSENT;
    }

    public boolean qualified() {
        return this.state != ProviderAuditState.DEGRADED;
    }

    public String diagnostic() {
        return "specialist=" + this.providerModId
            + " version=" + (this.version.isEmpty() ? "not-loaded" : this.version)
            + " audit=" + this.state.id()
            + " role=" + this.role
            + " detail=" + this.detail;
    }
}
