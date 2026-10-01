// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.compat;

import java.util.List;
import java.util.Objects;

/** Immutable ownership policy for one target-mod/domain pair. */
public record CompatibilityDomainPolicy(
    CompatibilityState fallbackState,
    String fallbackProvider,
    List<String> dominantProviders,
    boolean extensionOnlyWhenDominant
) {
    public CompatibilityDomainPolicy {
        Objects.requireNonNull(fallbackState, "fallbackState");
        fallbackProvider = Objects.requireNonNull(fallbackProvider, "fallbackProvider").trim();
        if (fallbackProvider.isEmpty()) throw new IllegalArgumentException("fallbackProvider is blank");
        dominantProviders = List.copyOf(Objects.requireNonNull(dominantProviders, "dominantProviders"));
    }

    public static CompatibilityDomainPolicy aaFallback() {
        return new CompatibilityDomainPolicy(
            CompatibilityState.AA_FALLBACK, "apothic_ascension", List.of(), false);
    }

    public static CompatibilityDomainPolicy aaFallbackYieldingTo(String... dominantProviders) {
        return new CompatibilityDomainPolicy(
            CompatibilityState.AA_FALLBACK, "apothic_ascension", List.of(dominantProviders), false);
    }

    public static CompatibilityDomainPolicy aaExtensionOnly() {
        return new CompatibilityDomainPolicy(
            CompatibilityState.AA_EXTENSION_ONLY, "apothic_ascension", List.of(), false);
    }

    public static CompatibilityDomainPolicy nativeOwnedBy(String nativeProvider) {
        return new CompatibilityDomainPolicy(
            CompatibilityState.NATIVE, nativeProvider, List.of(), false);
    }

    public static CompatibilityDomainPolicy nativeOwnedByYieldingTo(
        String nativeProvider, String... dominantProviders
    ) {
        return new CompatibilityDomainPolicy(
            CompatibilityState.NATIVE, nativeProvider, List.of(dominantProviders), false);
    }
}
