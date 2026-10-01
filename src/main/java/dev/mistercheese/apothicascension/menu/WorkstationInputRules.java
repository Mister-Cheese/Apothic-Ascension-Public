// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.menu;

import dev.mistercheese.apothicascension.AscensionRarity;
import dev.mistercheese.apothicascension.registry.ModItems;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import net.minecraft.world.item.ItemStack;

/** Type-level slot admission rules. Recipe-level cross-slot validation remains in WorkstationProcessor. */
public final class WorkstationInputRules {
    public static boolean accepts(WorkstationKind kind, int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        if (slot < 0 || slot >= WorkstationProcessor.INPUT_COUNT) return false;
        return switch (kind) {
            case ASCENSION_FORGE -> forge(slot, stack);
            case GEM_RESONATOR -> resonator(slot, stack);
            case AFFIX_LOOM -> loom(slot, stack);
            case APEX_ASCENSION_BENCH -> apex(slot, stack);
        };
    }

    private static boolean forge(int slot, ItemStack stack) {
        return switch (slot) {
            case 0 -> ModItems.rarityForMaterial(stack) != null;
            case 1 -> stack.getItem() == ModItems.ASCENSION_CORE.get();
            case 2 -> stack.getItem() == ModItems.STABILITY_THREAD.get();
            default -> false;
        };
    }

    private static boolean resonator(int slot, ItemStack stack) {
        return switch (slot) {
            case 0 -> stack.getItem() instanceof GemItem && GemItem.getPurity(stack) == Purity.PERFECT;
            case 1 -> {
                AscensionRarity rarity = ModItems.rarityForMaterial(stack);
                yield rarity != null && rarity != AscensionRarity.TRANSCENDENT && rarity != AscensionRarity.APOTHEOTIC;
            }
            case 2 -> stack.getItem() == ModItems.GEM_ASCENSION_MATRIX.get();
            default -> false;
        };
    }

    private static boolean loom(int slot, ItemStack stack) {
        return switch (slot) {
            case 0 -> stack.getItem() == ModItems.STABILITY_THREAD.get();
            case 1 -> stack.getItem() == ModItems.EVOLUTION_CATALYST.get();
            case 2 -> stack.getItem() == ModItems.ASCENSION_CORE.get();
            default -> false;
        };
    }

    private static boolean apex(int slot, ItemStack stack) {
        return switch (slot) {
            case 0 -> ModItems.rarityForMaterial(stack) != null
                || stack.getItem() instanceof GemItem && GemItem.getPurity(stack) == Purity.PERFECT
                || !LootCategory.forItem(stack).isNone();
            case 1 -> {
                AscensionRarity rarity = ModItems.rarityForMaterial(stack);
                yield stack.getItem() == ModItems.ASCENSION_CORE.get()
                    || rarity == AscensionRarity.TRANSCENDENT
                    || rarity == AscensionRarity.APOTHEOTIC;
            }
            case 2 -> stack.getItem() == ModItems.GEM_ASCENSION_MATRIX.get()
                || stack.is(Apoth.Items.SIGIL_OF_REBIRTH);
            default -> false;
        };
    }

    private WorkstationInputRules() {}
}
