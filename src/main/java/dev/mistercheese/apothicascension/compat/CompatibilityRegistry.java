// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.compat;

import dev.mistercheese.apothicascension.RuntimeCompatibility;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Canonical Beta 13 compatibility policy registry.
 *
 * <p>The registry contains only stable strings, enums and numeric version bands. Optional-mod
 * classes are never referenced here. Specialist providers are independently version-audited before
 * they may become authoritative for any target/domain pair.</p>
 */
public final class CompatibilityRegistry {
    public static final String POLICY_REVISION = "compat-provider-v3";

    private static final List<CompatibilityProvider> PROVIDERS = List.of(
        provider("irons_apothic", "Iron's Apothic", "2.2.1", "2.2.2",
            "irons_affix_and_gem_semantics"),
        provider("ars_n_spells", "Ars 'n Spells", "3.3.0", "3.3.1",
            "ars_irons_magic_resource_semantics"),
        provider("apothic_category_compat", "Apothic Category Compat", "2.1.0", "2.1.1",
            "apotheosis_category_specialist"),
        provider("extra_apoth_compat", "Extra Apoth Compat", "1.0.3", "1.0.4",
            "emi_apotheosis_presentation"),
        provider("fallen_gems_affixes", "Fallen Gems & Affixes", "1.0.0", "1.0.1",
            "affix_and_gem_addon_coexistence")
    );

    private static final List<CompatibilityTarget> TARGETS = List.of(
        target("ars_nouveau", "Ars Nouveau", "5.12", "6", Map.of(
            CompatibilityDomain.AFFIX_CONTENT, CompatibilityDomainPolicy.aaFallback(),
            CompatibilityDomain.MAGIC_RESOURCE,
                CompatibilityDomainPolicy.nativeOwnedByYieldingTo("ars_nouveau", "ars_n_spells"))),
        target("irons_spellbooks", "Iron's Spells 'n Spellbooks", "1.21.1-3.16.1", "1.21.1-4", Map.of(
            CompatibilityDomain.AFFIX_CONTENT,
                CompatibilityDomainPolicy.aaFallbackYieldingTo("irons_apothic"),
            CompatibilityDomain.MAGIC_RESOURCE,
                CompatibilityDomainPolicy.nativeOwnedByYieldingTo("irons_spellbooks", "ars_n_spells"))),
        target("spartan_weaponry_unofficial", "Spartan Weaponry Unofficial", "1.2.3", "2", Map.of(
            CompatibilityDomain.CATEGORY, CompatibilityDomainPolicy.aaFallback())),
        target("allthemodium", "Allthemodium", "3.0.1", "3.1.1", Map.of(
            CompatibilityDomain.CATEGORY, CompatibilityDomainPolicy.aaFallback())),
        target("simplyswords", "Simply Swords", "1.70.2-1.21.1", "1.71", Map.of(
            CompatibilityDomain.ITEM_STATE,
                CompatibilityDomainPolicy.nativeOwnedBy("simplyswords"))),
        target("silentgear", "Silent Gear", "4.1.5", "4.2", Map.of(
            CompatibilityDomain.ITEM_STATE,
                CompatibilityDomainPolicy.nativeOwnedBy("silentgear"))),
        target("jei", "Just Enough Items", "19.27.0.343", "20", Map.of(
            CompatibilityDomain.RECIPE_VIEWER, CompatibilityDomainPolicy.aaExtensionOnly()))
    );

    static {
        for (CompatibilityTarget target : TARGETS) {
            for (CompatibilityDomainPolicy policy : target.domains().values()) {
                for (String dominant : policy.dominantProviders()) {
                    if (provider(dominant).isEmpty()) {
                        throw new IllegalStateException("unregistered dominant compatibility provider: " + dominant);
                    }
                }
            }
        }
    }

    private CompatibilityRegistry() {}

    public static List<CompatibilityTarget> targets() {
        return TARGETS;
    }

    public static List<CompatibilityProvider> providers() {
        return PROVIDERS;
    }

    public static Optional<CompatibilityProvider> provider(String modId) {
        return PROVIDERS.stream().filter(provider -> provider.modId().equals(modId)).findFirst();
    }

    private static CompatibilityProvider provider(
        String modId,
        String displayName,
        String minimumInclusive,
        String maximumExclusive,
        String role
    ) {
        return new CompatibilityProvider(
            modId,
            displayName,
            new RuntimeCompatibility.VersionBand(minimumInclusive, maximumExclusive, false),
            role);
    }

    private static CompatibilityTarget target(
        String modId,
        String displayName,
        String minimumInclusive,
        String maximumExclusive,
        Map<CompatibilityDomain, CompatibilityDomainPolicy> domainPolicies
    ) {
        EnumMap<CompatibilityDomain, CompatibilityDomainPolicy> domains = new EnumMap<>(CompatibilityDomain.class);
        domains.putAll(domainPolicies);
        return new CompatibilityTarget(
            modId,
            displayName,
            new RuntimeCompatibility.VersionBand(minimumInclusive, maximumExclusive, false),
            domains);
    }
}
