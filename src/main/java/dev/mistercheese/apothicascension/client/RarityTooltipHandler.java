// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.client;

import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.AscensionRarity;
import dev.mistercheese.apothicascension.GemTier;
import dev.mistercheese.apothicascension.client.AscensionTooltipPolicy.Decision;
import dev.mistercheese.apothicascension.client.AscensionTooltipPolicy.Scope;
import dev.mistercheese.apothicascension.config.AscensionClientConfig;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Client-only presentation for rarity state that is explicitly owned by Apothic Ascension.
 *
 * <p>The handler is intentionally additive and ownership-scoped. It does not decorate ordinary
 * stacks, stock Apotheosis rarities, vanilla rarity values, or third-party rarity ladders. It
 * also never deletes, reorders, string-matches, or flattens foreign tooltip content.</p>
 */
@EventBusSubscriber(modid = ApothicAscension.MODID, value = Dist.CLIENT)
public final class RarityTooltipHandler {
    private static final int LABEL_COLOR = 0x777777;

    private RarityTooltipHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Decision decision = AscensionTooltipPolicy.classify(stack);
        if (!decision.isAscensionOwned()) return;

        AscensionRarity rarity = decision.rarity();
        if (AscensionClientConfig.decorateAscensionItemNames()) {
            decorateTitleWithoutReplacingForeignContent(event.getToolTip(), rarity);
        }

        // Common is deliberately implicit. More importantly, no vanilla/stock rarity reaches
        // this branch at all: only an Ascension-owned semantic state may emit our rarity line.
        if (!AscensionClientConfig.showAscensionRarityLabels()) return;

        Component rarityComponent = resolveOwnedRarityComponent(stack, decision);
        if (rarityComponent == null) return;

        // Add exactly one owned line and leave every pre-existing line in its original order.
        event.getToolTip().add(
            Component.translatable("tooltip.apothic_ascension.rarity", rarityComponent)
                .withStyle(Style.EMPTY.withColor(LABEL_COLOR))
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        if (!AscensionClientConfig.decorateAscensionTooltipFrames()) return;

        Decision decision = AscensionTooltipPolicy.classify(event.getItemStack());
        if (!decision.isAscensionOwned()) return;
        AscensionRarity rarity = decision.rarity();

        double phase = animationPhase(rarity);
        int top = animatedColor(rarity, 0.08D, phase);
        int bottom = animatedColor(rarity, 0.72D, phase);
        event.setBorderStart(argb(0xFF, top));
        event.setBorderEnd(argb(0xFF, bottom));

        // Keep the body readable while giving explicitly-owned Ascension stacks a distinct frame.
        int bgTop = mix(0x080810, top, rarity.rank() >= 13 ? 0.16D : 0.09D);
        int bgBottom = mix(0x050508, bottom, rarity.rank() >= 13 ? 0.12D : 0.06D);
        event.setBackgroundStart(argb(0xEC, bgTop));
        event.setBackgroundEnd(argb(0xEC, bgBottom));
    }

    /**
     * Wraps the original title component instead of converting it to a String and rebuilding it.
     * This preserves nested components, styles, localization, hover/click metadata, and any other
     * title semantics supplied by Minecraft or another mod. Only our prefix/suffix are colored.
     */
    private static void decorateTitleWithoutReplacingForeignContent(List<Component> tooltip, AscensionRarity rarity) {
        if (tooltip.isEmpty()) return;
        Component original = tooltip.getFirst();
        if (original == null) return;

        double phase = animationPhase(rarity);
        MutableComponent decorated = Component.empty();
        decorated.append(gradient(titlePrefix(rarity), rarity, phase));
        decorated.append(original.copy());
        decorated.append(gradient(titleSuffix(rarity), rarity, fract(phase + 0.5D)));
        tooltip.set(0, decorated);
    }

    private static Component resolveOwnedRarityComponent(ItemStack stack, Decision decision) {
        if (decision.scope() == Scope.ASCENSION_AFFIX) {
            DynamicHolder<LootRarity> holder = AffixHelper.getRarity(stack);
            if (holder.isBound()) return holder.get().toComponent();
        }

        // Materials and extended gems are first-party states rather than Apotheosis affix stacks,
        // so they use the Ascension translation/color authored for the corresponding tier.
        AscensionRarity rarity = decision.rarity();
        GemTier tier = GemTier.byId(rarity.postMythicIndex());
        return Component.translatable("rarity.apothic_ascension:" + rarity.key())
            .withStyle(Style.EMPTY.withColor(tier.color()));
    }

    private static String titlePrefix(AscensionRarity rarity) {
        return switch (rarity) {
            case LEGENDARY -> "✦ ";
            case ANCIENT -> "◆ ";
            case FORGOTTEN -> "◇ ";
            case PRIMAL, STELLAR, DIVINE -> "✧ ";
            case ESOTERIC, CATACLYSMIC, ABYSSAL -> "✦◆ ";
            case EMPYREAN, PARACAUSAL, TRANSCENDENT -> "✦◇✦ ";
            case APOTHEOTIC -> "✦◆✦ ";
        };
    }

    private static String titleSuffix(AscensionRarity rarity) {
        return switch (rarity) {
            case LEGENDARY -> " ✦";
            case ANCIENT -> " ◆";
            case FORGOTTEN -> " ◇";
            case PRIMAL, STELLAR, DIVINE -> " ✧";
            case ESOTERIC, CATACLYSMIC, ABYSSAL -> " ◆✦";
            case EMPYREAN, PARACAUSAL, TRANSCENDENT -> " ✦◇✦";
            case APOTHEOTIC -> " ✦◆✦";
        };
    }

    private static MutableComponent gradient(String text, AscensionRarity rarity, double phase) {
        MutableComponent out = Component.empty();
        int[] cps = text.codePoints().toArray();
        int denom = Math.max(1, cps.length - 1);
        for (int i = 0; i < cps.length; i++) {
            double pos = i / (double) denom;
            int color = animatedColor(rarity, pos, phase);
            out.append(Component.literal(new String(Character.toChars(cps[i]))).withStyle(Style.EMPTY.withColor(color)));
        }
        return out;
    }

    private static double animationPhase(AscensionRarity rarity) {
        if (rarity.rank() < 9) return 0.0D;
        long periodNanos = rarity.rank() >= 16 ? 12_000_000_000L : rarity.rank() >= 13 ? 16_000_000_000L : 20_000_000_000L;
        long now = System.nanoTime();
        return Math.floorMod(now, periodNanos) / (double) periodNanos;
    }

    private static int animatedColor(AscensionRarity rarity, double position, double phase) {
        if (rarity == AscensionRarity.APOTHEOTIC) {
            return hsvToRgb(fract(position * 0.92D + phase), 0.72D, 1.0D);
        }
        int[] palette = palette(rarity);
        if (palette.length == 1) return palette[0];
        double x = fract(position * (palette.length - 1) + phase * (rarity.rank() >= 9 ? 1.0D : 0.0D));
        double scaled = x * palette.length;
        int a = ((int) Math.floor(scaled)) % palette.length;
        int b = (a + 1) % palette.length;
        return mix(palette[a], palette[b], scaled - Math.floor(scaled));
    }

    private static int[] palette(AscensionRarity rarity) {
        return switch (rarity) {
            case LEGENDARY -> new int[]{0xFFDE91, 0xFFF3C4};
            case ANCIENT -> new int[]{0xC33A3A, 0xFF8B6A};
            case FORGOTTEN -> new int[]{0x8A5A44, 0xD1A17F};
            case PRIMAL -> new int[]{0xE85DFF, 0x8C52FF, 0xFF9AF2};
            case STELLAR -> new int[]{0x4DA6FF, 0xB7E5FF, 0x826BFF};
            case DIVINE -> new int[]{0xFFF2A8, 0xFFFFFF, 0xFFD65A};
            case ESOTERIC -> new int[]{0x30C7B5, 0x9CFFF1, 0x725CFF};
            case CATACLYSMIC -> new int[]{0xFF4FD8, 0xFF704D, 0x8A55FF, 0x4DEBFF};
            case ABYSSAL -> new int[]{0x2B103F, 0x7040A8, 0xD074FF, 0x2A0B45};
            case EMPYREAN -> new int[]{0xFFD6FF, 0xA8FBFF, 0xFFF3B0, 0xD8B6FF};
            case PARACAUSAL -> new int[]{0x66FFCC, 0xB8FF66, 0x66B3FF, 0xD5FFEE};
            case TRANSCENDENT -> new int[]{0xD8FFFF, 0xFFFFFF, 0xA6B7FF, 0xFFD6FF};
            case APOTHEOTIC -> new int[]{0xFFFFFF}; // handled by HSV path above
        };
    }

    private static double fract(double v) {
        return v - Math.floor(v);
    }

    private static int mix(int a, int b, double t) {
        t = Math.max(0.0D, Math.min(1.0D, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) Math.round(ar + (br - ar) * t);
        int g = (int) Math.round(ag + (bg - ag) * t);
        int bl = (int) Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int argb(int alpha, int rgb) {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    private static int hsvToRgb(double h, double s, double v) {
        h = fract(h) * 6.0D;
        int sector = (int) Math.floor(h);
        double f = h - sector;
        double p = v * (1.0D - s);
        double q = v * (1.0D - s * f);
        double t = v * (1.0D - s * (1.0D - f));
        double r, g, b;
        switch (sector % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) Math.round(r * 255.0D) << 16) | ((int) Math.round(g * 255.0D) << 8) | (int) Math.round(b * 255.0D);
    }
}
