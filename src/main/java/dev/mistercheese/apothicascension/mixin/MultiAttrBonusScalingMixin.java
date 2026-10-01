// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.mixin;

import dev.mistercheese.apothicascension.GemTierScaling;
import dev.shadowsoffire.apotheosis.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.socket.gem.GemView;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.MultiAttrBonus;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.MultiAttrBonus.ModifierInst;
import dev.shadowsoffire.apothic_attributes.modifiers.StackAttributeModifiersEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MultiAttrBonus.class, remap = false)
public abstract class MultiAttrBonusScalingMixin {
    @Redirect(method = "addModifiers", at = @At(value = "INVOKE", target = "Ldev/shadowsoffire/apotheosis/socket/gem/bonus/MultiAttrBonus$ModifierInst;build(Lnet/minecraft/resources/ResourceLocation;Ldev/shadowsoffire/apotheosis/socket/gem/Purity;)Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;"), require = 1, allow = 1, remap = false)
    private AttributeModifier apothicAscension$modifier(ModifierInst modifier, ResourceLocation id, Purity purity, GemInstance gem, StackAttributeModifiersEvent event) {
        AttributeModifier base = modifier.build(id, purity);
        double amount = GemTierScaling.scaleAttribute(modifier.attr(), modifier.op(), base.amount(), gem);
        return new AttributeModifier(base.id(), amount, base.operation());
    }

    @Redirect(method = "getSocketBonusTooltip", at = @At(value = "INVOKE", target = "Ldev/shadowsoffire/apotheosis/socket/gem/bonus/MultiAttrBonus$ModifierInst;build(Lnet/minecraft/resources/ResourceLocation;Ldev/shadowsoffire/apotheosis/socket/gem/Purity;)Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;"), require = 1, allow = 1, remap = false)
    private AttributeModifier apothicAscension$tooltip(ModifierInst modifier, ResourceLocation id, Purity purity, GemView gem, AttributeTooltipContext ctx) {
        AttributeModifier base = modifier.build(id, purity);
        double amount = GemTierScaling.scaleAttribute(modifier.attr(), modifier.op(), base.amount(), gem);
        return new AttributeModifier(base.id(), amount, base.operation());
    }
}
