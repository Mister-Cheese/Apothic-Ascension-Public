// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.mixin;

import dev.mistercheese.apothicascension.GemTierScaling;
import dev.shadowsoffire.apotheosis.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.socket.gem.GemView;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.special.BloodyArrowBonus;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BloodyArrowBonus.class, remap = false)
public abstract class BloodyArrowBonusScalingMixin {
    @Redirect(method = "onProjectileFired", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), require = 1, allow = 1, remap = false)
    private Object apothicAscension$projectile(Map<Purity, BloodyArrowBonus.Data> values, Object key, GemInstance gem, LivingEntity user, Projectile projectile) {
        return GemTierScaling.scaleBloodyArrow(values.get(key), gem);
    }

    @Redirect(method = "getSocketBonusTooltip", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), require = 1, allow = 1, remap = false)
    private Object apothicAscension$tooltip(Map<Purity, BloodyArrowBonus.Data> values, Object key, GemView gem, AttributeTooltipContext ctx) {
        return GemTierScaling.scaleBloodyArrow(values.get(key), gem);
    }
}
