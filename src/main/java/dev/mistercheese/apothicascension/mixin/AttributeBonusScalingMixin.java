// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.mixin;

import dev.mistercheese.apothicascension.GemTierScaling;
import dev.shadowsoffire.apotheosis.socket.gem.GemView;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.AttributeBonus;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = AttributeBonus.class, remap = false)
public abstract class AttributeBonusScalingMixin {
    @Shadow @Final protected Holder<Attribute> attribute;
    @Shadow @Final protected Operation operation;

    @Redirect(
        method = "createModifier",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
        require = 1,
        allow = 1,
        remap = false)
    private Object apothicAscension$scaleAttribute(Map<Purity, Double> values, Object key, GemView gem) {
        Double base = values.get(key);
        return base == null ? null : GemTierScaling.scaleAttribute(this.attribute, this.operation, base, gem);
    }
}
