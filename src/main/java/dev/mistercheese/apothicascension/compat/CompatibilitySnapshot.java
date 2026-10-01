// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.compat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, reload-safe compatibility resolution result. */
public record CompatibilitySnapshot(
    String buildId,
    String policyRevision,
    Map<String, String> loadedVersions,
    List<CompatibilityProviderStatus> providers,
    List<CompatibilityDecision> decisions
) {
    public CompatibilitySnapshot {
        buildId = Objects.requireNonNull(buildId, "buildId");
        policyRevision = Objects.requireNonNull(policyRevision, "policyRevision");
        loadedVersions = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(loadedVersions, "loadedVersions")));
        providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
        decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
    }

    public Optional<CompatibilityDecision> decision(String targetModId, CompatibilityDomain domain) {
        return this.decisions.stream()
            .filter(d -> d.targetModId().equals(targetModId) && d.domain() == domain)
            .findFirst();
    }

    public Optional<CompatibilityProviderStatus> provider(String providerModId) {
        return this.providers.stream()
            .filter(provider -> provider.providerModId().equals(providerModId))
            .findFirst();
    }

    public long degradedDecisionCount() {
        return this.decisions.stream().filter(d -> d.state() == CompatibilityState.DEGRADED).count();
    }

    public long degradedProviderCount() {
        return this.providers.stream().filter(provider -> provider.state() == ProviderAuditState.DEGRADED).count();
    }

    public long degradedCount() {
        return degradedDecisionCount() + degradedProviderCount();
    }
}
