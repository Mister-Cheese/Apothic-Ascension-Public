// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.registry;

import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.GemTier;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Stable data-driven item classification used by progression and recipes. */
public final class ModTags {
    public static final TagKey<Item> CELESTIAL_STAGE_MATERIALS = item("progression/celestial_materials");
    public static final TagKey<Item> ESOTERIC_STAGE_MATERIALS = item("progression/esoteric_materials");
    public static final TagKey<Item> TRANSCENDENT_STAGE_MATERIALS = item("progression/transcendent_materials");
    public static final TagKey<Item> APOTHEOTIC_STAGE_MATERIALS = item("progression/apotheotic_materials");

    private static final Map<GemTier, TagKey<Item>> GEM_MATERIALS = new EnumMap<>(GemTier.class);

    static {
        for (GemTier tier : GemTier.values()) {
            if (tier != GemTier.PERFECT) GEM_MATERIALS.put(tier, item("gem_upgrade_materials/" + tier.key()));
        }
    }

    private ModTags() {}

    public static TagKey<Item> gemUpgradeMaterial(GemTier tier) {
        TagKey<Item> tag = GEM_MATERIALS.get(tier);
        if (tag == null) throw new IllegalArgumentException("Perfect has no gem upgrade material");
        return tag;
    }

    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ApothicAscension.MODID, path));
    }
}
