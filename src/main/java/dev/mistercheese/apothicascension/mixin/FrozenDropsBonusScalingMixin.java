// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.mixin;

import dev.mistercheese.apothicascension.GemTierScaling;
import dev.shadowsoffire.apotheosis.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.socket.gem.GemView;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.special.FrozenDropsBonus;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = FrozenDropsBonus.class, remap = false)
public abstract class FrozenDropsBonusScalingMixin {
    @Redirect(method = "getSocketBonusTooltip", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), require = 1, allow = 1, remap = false)
    private Object apothicAscension$tooltip(Map<Purity, Float> values, Object key, GemView gem, AttributeTooltipContext ctx) {
        Float base = values.get(key);
        return base == null ? null : GemTierScaling.scaleFrozenDrops(base, gem);
    }

    @Redirect(method = "modifyLoot", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), require = 1, allow = 1, remap = false)
    private Object apothicAscension$loot(Map<Purity, Float> values, Object key, GemInstance gem, ObjectArrayList<ItemStack> loot, LootContext ctx) {
        Float base = values.get(key);
        return base == null ? null : GemTierScaling.scaleFrozenDrops(base, gem);
    }
}
