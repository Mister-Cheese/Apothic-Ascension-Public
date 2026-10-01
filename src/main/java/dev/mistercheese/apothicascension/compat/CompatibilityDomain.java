// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.compat;

/**
 * Stable compatibility ownership domains.
 *
 * <p>Compatibility is resolved per domain rather than as one global enabled/disabled switch so a
 * specialist bridge can own one semantic surface without suppressing unrelated AA integration.</p>
 */
public enum CompatibilityDomain {
    CATEGORY("category"),
    AFFIX_CONTENT("affix_content"),
    AFFIX_EXTENSION("affix_extension"),
    GEM_CONTENT("gem_content"),
    GEM_EXTENSION("gem_extension"),
    RECIPE_VIEWER("recipe_viewer"),
    MAGIC_RESOURCE("magic_resource"),
    ITEM_STATE("item_state");

    private final String id;

    CompatibilityDomain(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}
