// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/**
 * Scales only the stack's vanilla/base combat attributes, then applies a final ergonomic
 * safety envelope to non-default kinematic modifiers on Ascension-rarity items.
 *
 * <p>The safety envelope is intentionally a last-line guard, not the primary balance system.
 * JSON affix data is authored below these limits; this catches malformed datapacks, future
 * generator regressions, or multiple same-purpose affixes combining into physics-breaking
 * values such as the pre-7.1 +1890% Windswept roll.</p>
 */
@EventBusSubscriber(modid = ApothicAscension.MODID)
public final class IntrinsicScalingHandler {
    private record ModifierKey(Holder<Attribute> attribute, ResourceLocation id) {}
    private record GroupKey(Holder<Attribute> attribute, AttributeModifier.Operation operation) {}
    private record Cap(double positive, double negativeMagnitude) {
        static Cap positive(double v) { return new Cap(v, 0.0D); }
        static Cap negative(double v) { return new Cap(0.0D, v); }
    }

    private IntrinsicScalingHandler() {}

    /**
     * LOWEST is deliberate: intrinsic replacements are id-stable, and the ergonomic guard
     * needs to observe modifiers contributed by Apotheosis and other attribute providers.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttributes(ItemAttributeModifierEvent event) {
        AscensionRarity rarity = RarityResolver.ascensionRarity(event.getItemStack());
        if (rarity == null) return;

        scaleIntrinsicDefaults(event, rarity);
        clampKinematicAffixes(event, rarity);
    }

    private static void scaleIntrinsicDefaults(ItemAttributeModifierEvent event, AscensionRarity rarity) {
        for (ItemAttributeModifiers.Entry e : new ArrayList<>(event.getDefaultModifiers().modifiers())) {
            Holder<Attribute> attribute = e.attribute();
            double factor;
            if (attribute.equals(Attributes.ARMOR)) factor = rarity.armorMultiplier();
            else if (attribute.equals(Attributes.ARMOR_TOUGHNESS)) factor = rarity.toughnessMultiplier();
            else if (attribute.equals(Attributes.ATTACK_DAMAGE)) factor = rarity.attackMultiplier();
            else factor = 1.0D;

            if (factor == 1.0D) continue;
            AttributeModifier m = e.modifier();
            double amount = finiteOrZero(m.amount());
            double scaled = amount * factor;
            // AA must never turn a finite foreign/default value into infinity through its own
            // multiplier. Preserve the original finite amount if multiplication overflows.
            if (!Double.isFinite(scaled)) scaled = amount;
            event.replaceModifier(attribute, new AttributeModifier(m.id(), scaled, m.operation()), e.slot());
        }
    }

    private static void clampKinematicAffixes(ItemAttributeModifierEvent event, AscensionRarity rarity) {
        Set<ModifierKey> defaults = new HashSet<>();
        for (ItemAttributeModifiers.Entry e : event.getDefaultModifiers().modifiers()) {
            defaults.add(new ModifierKey(e.attribute(), e.modifier().id()));
        }

        Map<GroupKey, List<ItemAttributeModifiers.Entry>> groups = new HashMap<>();
        for (ItemAttributeModifiers.Entry e : new ArrayList<>(event.getModifiers())) {
            if (defaults.contains(new ModifierKey(e.attribute(), e.modifier().id()))) continue;
            Cap cap = capFor(e.attribute(), e.modifier().operation(), rarity);
            if (cap == null) continue;
            groups.computeIfAbsent(new GroupKey(e.attribute(), e.modifier().operation()), ignored -> new ArrayList<>()).add(e);
        }

        for (List<ItemAttributeModifiers.Entry> entries : groups.values()) {
            if (entries.isEmpty()) continue;
            AttributeModifier sample = entries.getFirst().modifier();
            Cap cap = capFor(entries.getFirst().attribute(), sample.operation(), rarity);
            if (cap == null) continue;

            // A malformed runtime modifier must never poison the cap accumulator.  We deliberately
            // neutralize only non-finite modifiers in groups that Ascension already owns as an
            // ergonomic safety envelope.  Finite foreign modifiers remain intact unless their
            // aggregate exceeds the documented cap.
            for (ItemAttributeModifiers.Entry e : entries) {
                AttributeModifier m = e.modifier();
                if (Double.isFinite(m.amount())) continue;
                event.replaceModifier(e.attribute(), new AttributeModifier(m.id(), 0.0D, m.operation()), e.slot());
            }

            if (cap.positive() > 0.0D) {
                double total = entries.stream().map(ItemAttributeModifiers.Entry::modifier).mapToDouble(m -> finiteOrZero(m.amount())).filter(v -> v > 0.0D).sum();
                if (total > cap.positive()) {
                    double scale = cap.positive() / total;
                    for (ItemAttributeModifiers.Entry e : entries) {
                        AttributeModifier m = e.modifier();
                        double amount = finiteOrZero(m.amount());
                        if (amount <= 0.0D) continue;
                        event.replaceModifier(e.attribute(), new AttributeModifier(m.id(), amount * scale, m.operation()), e.slot());
                    }
                }
            }
            else if (cap.negativeMagnitude() > 0.0D) {
                double total = entries.stream().map(ItemAttributeModifiers.Entry::modifier).mapToDouble(m -> finiteOrZero(m.amount())).filter(v -> v < 0.0D).sum();
                if (total < -cap.negativeMagnitude()) {
                    double scale = (-cap.negativeMagnitude()) / total;
                    for (ItemAttributeModifiers.Entry e : entries) {
                        AttributeModifier m = e.modifier();
                        double amount = finiteOrZero(m.amount());
                        if (amount >= 0.0D) continue;
                        event.replaceModifier(e.attribute(), new AttributeModifier(m.id(), amount * scale, m.operation()), e.slot());
                    }
                }
            }
        }
    }

    private static double finiteOrZero(double amount) {
        return Double.isFinite(amount) ? amount : 0.0D;
    }

    private static Cap capFor(Holder<Attribute> attribute, AttributeModifier.Operation operation, AscensionRarity rarity) {
        double t = Math.max(0.0D, Math.min(1.0D, (rarity.postMythicIndex() - 1) / 12.0D));
        if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            if (attribute.equals(Attributes.MOVEMENT_SPEED)) return Cap.positive(0.35D + 0.50D * t);
            if (attribute.equals(Attributes.ATTACK_SPEED)) return Cap.positive(0.80D + 1.00D * t);
            if (attribute.equals(NeoForgeMod.SWIM_SPEED)) return Cap.positive(0.70D + 1.00D * t);
            if (attribute.equals(ALObjects.Attributes.DRAW_SPEED)) return Cap.positive(1.00D + 1.20D * t);
            if (attribute.equals(ALObjects.Attributes.ARROW_VELOCITY)) return Cap.positive(0.45D + 1.00D * t);
            if (attribute.equals(ALObjects.Attributes.MINING_SPEED) || attribute.equals(Attributes.BLOCK_BREAK_SPEED)) {
                return Cap.positive(1.40D + 2.20D * t);
            }
            if (attribute.equals(Attributes.GRAVITY)) return Cap.negative(0.55D + 0.30D * t);
        }
        if (operation == AttributeModifier.Operation.ADD_MULTIPLIED_BASE && attribute.equals(Attributes.BURNING_TIME)) {
            return Cap.negative(0.75D + 0.25D * t);
        }
        if (operation == AttributeModifier.Operation.ADD_VALUE) {
            if (attribute.equals(Attributes.STEP_HEIGHT)) return Cap.positive(2.00D + 1.50D * t);
            if (attribute.equals(Attributes.BLOCK_INTERACTION_RANGE)) return Cap.positive(4.0D + 5.0D * t);
            if (attribute.equals(Attributes.ENTITY_INTERACTION_RANGE)) return Cap.positive(3.0D + 4.0D * t);
            if (attribute.equals(Attributes.ATTACK_KNOCKBACK)) return Cap.positive(3.0D + 4.0D * t);
            if (attribute.equals(Attributes.KNOCKBACK_RESISTANCE)) return Cap.positive(0.60D + 0.35D * t);
        }
        return null;
    }

}
