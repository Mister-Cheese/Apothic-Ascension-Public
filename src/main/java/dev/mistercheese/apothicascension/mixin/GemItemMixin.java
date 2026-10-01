// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.mixin;

import dev.mistercheese.apothicascension.GemTier;
import dev.mistercheese.apothicascension.GemTierData;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extended grade presentation while retaining Apotheosis' actual GemItem implementation. */
@Mixin(value = GemItem.class, remap = false)
public abstract class GemItemMixin {
    @Inject(method = "getName", at = @At("TAIL"), cancellable = true, require = 1, allow = 1, remap = false)
    private void apothicAscension$extendedName(ItemStack stack, CallbackInfoReturnable<Component> cir) {
        int id = GemTierData.get(stack);
        if (id <= GemTier.PERFECT.id()) return;
        GemTier tier = GemTier.byId(id);
        GemItem self = (GemItem) (Object) this;
        Component base = Component.translatable(self.getDescriptionId(stack));
        cir.setReturnValue(Component.translatable("item.apothic_ascension.gem." + tier.key(), base)
            .withStyle(Style.EMPTY.withColor(tier.color())));
    }
}
