// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.registry;

import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.AscensionRarity;
import dev.mistercheese.apothicascension.item.AscensionMaterialItem;
import dev.mistercheese.apothicascension.item.GemAscensionMatrixItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ApothicAscension.MODID);

    public static final DeferredItem<Item> LEGENDARY_MATERIAL = material(AscensionRarity.LEGENDARY);
    public static final DeferredItem<Item> ANCIENT_MATERIAL = material(AscensionRarity.ANCIENT);
    public static final DeferredItem<Item> FORGOTTEN_MATERIAL = material(AscensionRarity.FORGOTTEN);
    public static final DeferredItem<Item> PRIMAL_MATERIAL = material(AscensionRarity.PRIMAL);
    public static final DeferredItem<Item> STELLAR_MATERIAL = material(AscensionRarity.STELLAR);
    public static final DeferredItem<Item> DIVINE_MATERIAL = material(AscensionRarity.DIVINE);
    public static final DeferredItem<Item> ESOTERIC_MATERIAL = material(AscensionRarity.ESOTERIC);
    public static final DeferredItem<Item> CATACLYSMIC_MATERIAL = material(AscensionRarity.CATACLYSMIC);
    public static final DeferredItem<Item> ABYSSAL_MATERIAL = material(AscensionRarity.ABYSSAL);
    public static final DeferredItem<Item> EMPYREAN_MATERIAL = material(AscensionRarity.EMPYREAN);
    public static final DeferredItem<Item> PARACAUSAL_MATERIAL = material(AscensionRarity.PARACAUSAL);
    public static final DeferredItem<Item> TRANSCENDENT_MATERIAL = material(AscensionRarity.TRANSCENDENT);
    public static final DeferredItem<Item> APOTHEOTIC_MATERIAL = material(AscensionRarity.APOTHEOTIC);

    public static final DeferredItem<Item> ASCENSION_SEAL = ITEMS.registerSimpleItem(
        "ascension_seal",
        new Item.Properties().stacksTo(1).fireResistant()
    );

    public static final DeferredItem<Item> ASCENSION_CODEX = ITEMS.registerSimpleItem(
        "ascension_codex",
        new Item.Properties().stacksTo(1).fireResistant()
    );

    public static final DeferredItem<Item> GEM_ASCENSION_MATRIX = ITEMS.register(
        "gem_ascension_matrix",
        () -> new GemAscensionMatrixItem(new Item.Properties().stacksTo(1).fireResistant())
    );

    public static final DeferredItem<Item> ASCENSION_CORE = ITEMS.registerSimpleItem(
        "ascension_core",
        new Item.Properties().stacksTo(1).fireResistant()
    );
    public static final DeferredItem<Item> STABILITY_THREAD = ITEMS.registerSimpleItem(
        "stability_thread",
        new Item.Properties().fireResistant()
    );
    public static final DeferredItem<Item> EVOLUTION_CATALYST = ITEMS.registerSimpleItem(
        "evolution_catalyst",
        new Item.Properties().stacksTo(16).fireResistant()
    );

    public static final DeferredItem<Item> ATTACK_FOCUS_SIGIL = focusSigil("attack_focus_sigil");
    public static final DeferredItem<Item> DEFENSE_FOCUS_SIGIL = focusSigil("defense_focus_sigil");
    public static final DeferredItem<Item> AGILITY_FOCUS_SIGIL = focusSigil("agility_focus_sigil");
    public static final DeferredItem<Item> SUSTAIN_FOCUS_SIGIL = focusSigil("sustain_focus_sigil");
    public static final DeferredItem<Item> UTILITY_FOCUS_SIGIL = focusSigil("utility_focus_sigil");

    public static final DeferredItem<BlockItem> ASCENSION_FORGE = ITEMS.registerSimpleBlockItem(
        "ascension_forge", ModBlocks.ASCENSION_FORGE, new Item.Properties().fireResistant()
    );
    public static final DeferredItem<BlockItem> GEM_RESONATOR = ITEMS.registerSimpleBlockItem(
        "gem_resonator", ModBlocks.GEM_RESONATOR, new Item.Properties().fireResistant()
    );
    public static final DeferredItem<BlockItem> AFFIX_LOOM = ITEMS.registerSimpleBlockItem(
        "affix_loom", ModBlocks.AFFIX_LOOM, new Item.Properties().fireResistant()
    );
    public static final DeferredItem<BlockItem> APEX_ASCENSION_BENCH = ITEMS.registerSimpleBlockItem(
        "apex_ascension_bench", ModBlocks.APEX_ASCENSION_BENCH, new Item.Properties().fireResistant()
    );
    public static final DeferredItem<BlockItem> ASCENSION_PYLON = ITEMS.registerSimpleBlockItem(
        "ascension_pylon", ModBlocks.ASCENSION_PYLON, new Item.Properties().fireResistant()
    );

    private static DeferredItem<Item> material(AscensionRarity rarity) {
        return ITEMS.register(
            rarity.key() + "_material",
            () -> new AscensionMaterialItem(rarity, new Item.Properties().fireResistant())
        );
    }

    private static DeferredItem<Item> focusSigil(String name) {
        return ITEMS.registerSimpleItem(name, new Item.Properties().stacksTo(16).fireResistant());
    }

    public static DeferredItem<Item> materialFor(AscensionRarity rarity) {
        return switch (rarity) {
            case LEGENDARY -> LEGENDARY_MATERIAL;
            case ANCIENT -> ANCIENT_MATERIAL;
            case FORGOTTEN -> FORGOTTEN_MATERIAL;
            case PRIMAL -> PRIMAL_MATERIAL;
            case STELLAR -> STELLAR_MATERIAL;
            case DIVINE -> DIVINE_MATERIAL;
            case ESOTERIC -> ESOTERIC_MATERIAL;
            case CATACLYSMIC -> CATACLYSMIC_MATERIAL;
            case ABYSSAL -> ABYSSAL_MATERIAL;
            case EMPYREAN -> EMPYREAN_MATERIAL;
            case PARACAUSAL -> PARACAUSAL_MATERIAL;
            case TRANSCENDENT -> TRANSCENDENT_MATERIAL;
            case APOTHEOTIC -> APOTHEOTIC_MATERIAL;
        };
    }

    public static AscensionRarity rarityForMaterial(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (AscensionRarity rarity : AscensionRarity.values()) {
            if (stack.getItem() == materialFor(rarity).get()) return rarity;
        }
        return null;
    }

    public static DeferredItem<Item> focusSigil(int family) {
        return switch (family) {
            case 0 -> ATTACK_FOCUS_SIGIL;
            case 1 -> DEFENSE_FOCUS_SIGIL;
            case 2 -> AGILITY_FOCUS_SIGIL;
            case 3 -> SUSTAIN_FOCUS_SIGIL;
            case 4 -> UTILITY_FOCUS_SIGIL;
            default -> throw new IllegalArgumentException("focus family must be 0..4");
        };
    }

    private ModItems() {}
}
