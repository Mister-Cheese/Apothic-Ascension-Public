// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.compat;

import dev.mistercheese.apothicascension.ApothicAscension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure provider resolver. Inputs are mod-id/version strings; no loader or registry APIs are used. */
public final class CompatibilityResolver {
    private CompatibilityResolver() {}

    public static CompatibilitySnapshot resolve(Map<String, String> loadedVersions) {
        Map<String, String> versions = new LinkedHashMap<>();
        loadedVersions.forEach((id, version) -> {
            if (id != null && version != null && !id.isBlank() && !version.isBlank()) {
                versions.put(id, version);
            }
        });

        List<CompatibilityProviderStatus> providerStatuses = resolveProviders(versions);
        Map<String, CompatibilityProviderStatus> providerById = new LinkedHashMap<>();
        providerStatuses.forEach(status -> providerById.put(status.providerModId(), status));

        List<CompatibilityDecision> decisions = new ArrayList<>();
        for (CompatibilityTarget target : CompatibilityRegistry.targets()) {
            String targetVersion = versions.getOrDefault(target.modId(), "");
            for (Map.Entry<CompatibilityDomain, CompatibilityDomainPolicy> entry : target.domains().entrySet()) {
                decisions.add(resolveDomain(
                    target, entry.getKey(), entry.getValue(), targetVersion, providerById));
            }
        }
        return new CompatibilitySnapshot(
            ApothicAscension.BUILD_ID,
            CompatibilityRegistry.POLICY_REVISION,
            versions,
            providerStatuses,
            decisions);
    }

    private static List<CompatibilityProviderStatus> resolveProviders(Map<String, String> versions) {
        List<CompatibilityProviderStatus> statuses = new ArrayList<>();
        for (CompatibilityProvider provider : CompatibilityRegistry.providers()) {
            String version = versions.getOrDefault(provider.modId(), "");
            if (version.isEmpty()) {
                statuses.add(providerStatus(provider, ProviderAuditState.ABSENT, version, "provider-not-loaded"));
            } else if (!provider.auditedBand().contains(version)) {
                statuses.add(providerStatus(
                    provider, ProviderAuditState.DEGRADED, version, "provider-version-outside-audited-band"));
            } else {
                statuses.add(providerStatus(provider, ProviderAuditState.AUDITED, version, "provider-version-audited"));
            }
        }
        return List.copyOf(statuses);
    }

    private static CompatibilityDecision resolveDomain(
        CompatibilityTarget target,
        CompatibilityDomain domain,
        CompatibilityDomainPolicy policy,
        String targetVersion,
        Map<String, CompatibilityProviderStatus> providerById
    ) {
        if (targetVersion.isEmpty()) {
            return decision(target, domain, CompatibilityState.DISABLED, "", targetVersion, "target-not-loaded");
        }
        if (!target.auditedBand().contains(targetVersion)) {
            return decision(target, domain, CompatibilityState.DEGRADED, "", targetVersion,
                "target-version-outside-audited-band");
        }

        for (String dominant : policy.dominantProviders()) {
            CompatibilityProviderStatus provider = providerById.get(dominant);
            if (provider == null) {
                return decision(target, domain, CompatibilityState.DEGRADED, dominant, targetVersion,
                    "dominant-provider-not-registered");
            }
            if (!provider.loaded()) continue;
            if (!provider.qualified()) {
                return decision(target, domain, CompatibilityState.DEGRADED, dominant, targetVersion,
                    "dominant-provider-version-outside-audited-band=" + provider.version());
            }
            if (policy.extensionOnlyWhenDominant()) {
                return decision(target, domain, CompatibilityState.AA_EXTENSION_ONLY,
                    ApothicAscension.MODID, targetVersion, "third-party-semantic-provider=" + dominant);
            }
            return decision(target, domain, CompatibilityState.THIRD_PARTY_DOMINANT,
                dominant, targetVersion, "audited-dominant-provider-loaded");
        }

        return decision(target, domain, policy.fallbackState(), policy.fallbackProvider(), targetVersion,
            "audited-fallback-provider");
    }

    private static CompatibilityProviderStatus providerStatus(
        CompatibilityProvider provider,
        ProviderAuditState state,
        String version,
        String detail
    ) {
        return new CompatibilityProviderStatus(
            provider.modId(), provider.displayName(), state, version, provider.role(), detail);
    }

    private static CompatibilityDecision decision(
        CompatibilityTarget target,
        CompatibilityDomain domain,
        CompatibilityState state,
        String provider,
        String version,
        String detail
    ) {
        return new CompatibilityDecision(
            target.modId(), target.displayName(), domain, state, provider, version, detail);
    }
}
