// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.compat;

import java.util.Objects;

/** One resolved target/domain ownership decision. */
public record CompatibilityDecision(
    String targetModId,
    String targetDisplayName,
    CompatibilityDomain domain,
    CompatibilityState state,
    String providerModId,
    String targetVersion,
    String detail
) {
    public CompatibilityDecision {
        targetModId = Objects.requireNonNull(targetModId, "targetModId");
        targetDisplayName = Objects.requireNonNull(targetDisplayName, "targetDisplayName");
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(state, "state");
        providerModId = Objects.requireNonNull(providerModId, "providerModId");
        targetVersion = Objects.requireNonNull(targetVersion, "targetVersion");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public boolean targetLoaded() {
        return !this.targetVersion.isEmpty();
    }

    public boolean qualified() {
        return this.state != CompatibilityState.DEGRADED;
    }

    public String diagnostic() {
        return "target=" + this.targetModId
            + " version=" + (this.targetVersion.isEmpty() ? "not-loaded" : this.targetVersion)
            + " domain=" + this.domain.id()
            + " state=" + this.state.id()
            + " provider=" + (this.providerModId.isEmpty() ? "none" : this.providerModId)
            + " detail=" + this.detail;
    }
}
