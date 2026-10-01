// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.menu;

import net.minecraft.network.chat.Component;

/** First-party Ascension workstations. No workstation owns ticking or persistent machine state. */
public enum WorkstationKind {
    ASCENSION_FORGE("ascension_forge", 0xE78734),
    GEM_RESONATOR("gem_resonator", 0xB05CEB),
    AFFIX_LOOM("affix_loom", 0x4BC7C7),
    APEX_ASCENSION_BENCH("apex_ascension_bench", 0xF0C75E);

    private final String key;
    private final int accentColor;

    WorkstationKind(String key, int accentColor) {
        this.key = key;
        this.accentColor = accentColor;
    }

    public String key() {
        return this.key;
    }

    public int accentColor() {
        return this.accentColor;
    }

    public Component title() {
        return Component.translatable("container.apothic_ascension." + this.key);
    }
}
