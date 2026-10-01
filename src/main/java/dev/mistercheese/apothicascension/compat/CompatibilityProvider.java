// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.compat;

import dev.mistercheese.apothicascension.RuntimeCompatibility;
import java.util.Objects;

/** Audited metadata for a specialist compatibility provider detected by AA. */
public record CompatibilityProvider(
    String modId,
    String displayName,
    RuntimeCompatibility.VersionBand auditedBand,
    String role
) {
    public CompatibilityProvider {
        modId = Objects.requireNonNull(modId, "modId").trim();
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        Objects.requireNonNull(auditedBand, "auditedBand");
        role = Objects.requireNonNull(role, "role").trim();
        if (modId.isEmpty() || displayName.isEmpty() || role.isEmpty()) {
            throw new IllegalArgumentException("blank specialist provider metadata");
        }
    }
}
