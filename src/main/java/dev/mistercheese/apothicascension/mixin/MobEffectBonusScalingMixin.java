// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.mixin;

import dev.mistercheese.apothicascension.GemTierScaling;
import dev.shadowsoffire.apotheosis.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.socket.gem.GemView;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.MobEffectBonus;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MobEffectBonus.class, remap = false)
public abstract class MobEffectBonusScalingMixin {
    @Shadow protected abstract int getCooldown(Purity purity);

    @Redirect(method = "getSocketBonusTooltip", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), require = 1, allow = 1, remap = false)
    private Object apothicAscension$tooltipData(Map<Purity, MobEffectBonus.EffectData> values, Object key, GemView gem, AttributeTooltipContext ctx) {
        MobEffectBonus.EffectData base = values.get(key);
        return GemTierScaling.scaleEffect(base, gem);
    }

    @Redirect(method = "getSocketBonusTooltip", at = @At(value = "INVOKE", target = "Ldev/shadowsoffire/apotheosis/socket/gem/bonus/MobEffectBonus;getCooldown(Ldev/shadowsoffire/apotheosis/socket/gem/Purity;)I"), require = 1, allow = 1, remap = false)
    private int apothicAscension$tooltipCooldown(MobEffectBonus self, Purity purity, GemView gem, AttributeTooltipContext ctx) {
        return GemTierScaling.scaleCooldown(this.getCooldown(purity), gem);
    }

    @Redirect(method = "applyEffect", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), require = 1, allow = 1, remap = false)
    private Object apothicAscension$effectData(Map<Purity, MobEffectBonus.EffectData> values, Object key, GemInstance gem, LivingEntity target) {
        MobEffectBonus.EffectData base = values.get(key);
        return GemTierScaling.scaleEffect(base, gem);
    }

    @Redirect(method = "applyEffect", at = @At(value = "INVOKE", target = "Ldev/shadowsoffire/apotheosis/socket/gem/bonus/MobEffectBonus;getCooldown(Ldev/shadowsoffire/apotheosis/socket/gem/Purity;)I"), require = 1, allow = 1, remap = false)
    private int apothicAscension$effectCooldown(MobEffectBonus self, Purity purity, GemInstance gem, LivingEntity target) {
        return GemTierScaling.scaleCooldown(this.getCooldown(purity), gem);
    }
}
