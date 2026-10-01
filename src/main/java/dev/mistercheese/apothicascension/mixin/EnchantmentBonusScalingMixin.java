// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.mixin;

import dev.mistercheese.apothicascension.GemTierScaling;
import dev.shadowsoffire.apotheosis.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.socket.gem.GemView;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.EnchantmentBonus;
import java.util.Map;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EnchantmentBonus.class, remap = false)
public abstract class EnchantmentBonusScalingMixin {
    @Redirect(method = "getSocketBonusTooltip", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), require = 1, allow = 1, remap = false)
    private Object apothicAscension$tooltip(Map<Purity, Integer> values, Object key, GemView gem, AttributeTooltipContext ctx) {
        Integer base = values.get(key);
        return base == null ? null : GemTierScaling.scaleEnchantment(base, gem);
    }

    @Redirect(method = "getEnchantmentLevels", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), require = 1, allow = 1, remap = false)
    private Object apothicAscension$levels(Map<Purity, Integer> values, Object key, GemInstance gem, GetEnchantmentLevelEvent event) {
        Integer base = values.get(key);
        return base == null ? null : GemTierScaling.scaleEnchantment(base, gem);
    }
}
