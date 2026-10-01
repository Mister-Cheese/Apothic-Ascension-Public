// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import dev.mistercheese.apothicascension.registry.ModDataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Typed extended gem-grade state with read-only migration support for pre-hardening stacks. */
public final class GemTierData {
    private static final String LEGACY_KEY = "apothic_ascension_gem_grade";

    private GemTierData() {}

    public static int get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return GemTier.PERFECT.id();

        Integer component = stack.get(ModDataComponents.GEM_GRADE.get());
        if (component != null) return clamp(component);

        // Compatibility reader only. New code never writes this raw CustomData key.
        CustomData legacy = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag legacyTag = legacy.copyTag();
        if (!legacyTag.contains(LEGACY_KEY, Tag.TAG_INT)) return GemTier.PERFECT.id();
        return clamp(legacyTag.getInt(LEGACY_KEY));
    }

    public static GemTier tier(ItemStack stack) {
        return GemTier.byId(get(stack));
    }

    public static void set(ItemStack stack, int tier) {
        if (stack == null || stack.isEmpty()) return;
        writeCanonical(stack, clamp(tier));
        removeLegacyKey(stack);
    }

    public static void advance(ItemStack stack) {
        set(stack, Math.min(GemTier.APOTHEOTIC.id(), get(stack) + 1));
    }

    /**
     * Canonicalizes any typed or legacy grade state in-place.
     *
     * <p>The typed component is authoritative when both representations exist. Invalid legacy
     * types are discarded instead of being coerced through {@code CompoundTag#getInt}; an
     * out-of-range typed value is clamped defensively even though normal codec-backed persistence
     * should never construct one. The method is intentionally explicit rather than being called
     * from {@link #get(ItemStack)}, keeping tooltip/JEI/read-only paths free of hidden mutation.</p>
     *
     * @return {@code true} if the stack required a canonicalization write.
     */
    public static boolean normalize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        Integer component = stack.get(ModDataComponents.GEM_GRADE.get());
        CustomData legacy = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag legacyTag = legacy.copyTag();
        boolean hasLegacy = legacyTag.contains(LEGACY_KEY);
        boolean hasLegacyInt = legacyTag.contains(LEGACY_KEY, Tag.TAG_INT);
        if (component == null && !hasLegacy) return false;

        int canonical = component != null
            ? clamp(component)
            : hasLegacyInt ? clamp(legacyTag.getInt(LEGACY_KEY)) : GemTier.PERFECT.id();
        boolean typedAlreadyCanonical = canonical == GemTier.PERFECT.id()
            ? component == null
            : Integer.valueOf(canonical).equals(component);
        if (typedAlreadyCanonical && !hasLegacy) return false;

        writeCanonical(stack, canonical);
        removeLegacyKey(stack);
        return true;
    }

    private static void writeCanonical(ItemStack stack, int tier) {
        if (tier == GemTier.PERFECT.id()) stack.remove(ModDataComponents.GEM_GRADE.get());
        else stack.set(ModDataComponents.GEM_GRADE.get(), tier);
    }

    private static void removeLegacyKey(ItemStack stack) {
        CustomData legacy = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = legacy.copyTag();
        if (!tag.contains(LEGACY_KEY)) return;
        tag.remove(LEGACY_KEY);
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static int clamp(int tier) {
        return Math.max(GemTier.PERFECT.id(), Math.min(GemTier.APOTHEOTIC.id(), tier));
    }
}
