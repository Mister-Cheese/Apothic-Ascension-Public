// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.compat;

import dev.mistercheese.apothicascension.RuntimeCompatibility;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Static audited policy for one optional target mod. */
public record CompatibilityTarget(
    String modId,
    String displayName,
    RuntimeCompatibility.VersionBand auditedBand,
    Map<CompatibilityDomain, CompatibilityDomainPolicy> domains
) {
    public CompatibilityTarget {
        modId = Objects.requireNonNull(modId, "modId").trim();
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        Objects.requireNonNull(auditedBand, "auditedBand");
        if (modId.isEmpty() || displayName.isEmpty()) throw new IllegalArgumentException("blank target identity");
        EnumMap<CompatibilityDomain, CompatibilityDomainPolicy> copy = new EnumMap<>(CompatibilityDomain.class);
        copy.putAll(Objects.requireNonNull(domains, "domains"));
        if (copy.isEmpty()) throw new IllegalArgumentException("target has no compatibility domains: " + modId);
        domains = Map.copyOf(copy);
    }
}
