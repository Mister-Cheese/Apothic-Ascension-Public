// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.mixin;

import dev.mistercheese.apothicascension.GemTierScaling;
import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.socket.gem.GemView;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.special.RadialBonus;
import dev.shadowsoffire.apotheosis.util.RadialUtil.RadialData;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RadialBonus.class, remap = false)
public abstract class RadialBonusScalingMixin {
    @Redirect(method = "getSocketBonusTooltip", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), require = 1, allow = 1, remap = false)
    private Object apothicAscension$tooltip(Map<Purity, RadialData> values, Object key, GemView gem, AttributeTooltipContext ctx) {
        return GemTierScaling.scaleRadial(values.get(key), gem);
    }

    /** Runs only on Apotheosis' cache miss path, so socket scanning is not added to every block break. */
    @Inject(method = "getRadialDataImpl", at = @At(value = "RETURN", ordinal = 0), cancellable = true, require = 1, allow = 1, remap = false)
    private static void apothicAscension$scaleCachedResult(ItemStack tool, CallbackInfoReturnable<RadialData> cir) {
        RadialData base = cir.getReturnValue();
        if (base == null) return;
        GemInstance radial = SocketHelper.getGems(tool).streamValidGems()
            .filter(gem -> gem.getBonus().orElse(null) instanceof RadialBonus)
            .findFirst()
            .orElse(null);
        if (radial != null) cir.setReturnValue(GemTierScaling.scaleRadial(base, radial));
    }
}
