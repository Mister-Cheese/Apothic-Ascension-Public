// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.mixin;

import dev.mistercheese.apothicascension.GemTierData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends Apotheosis's authoritative JEI gem subtype key without owning JEI's subtype registry.
 *
 * <p>{@link Pseudo} and the coerced context parameter keep this class loadable when JEI is absent.
 * When the pinned Apotheosis JEI target is present, the injection is strict: target drift is a
 * compatibility failure rather than a silently disabled integration.</p>
 */
@Pseudo
@Mixin(targets = "dev.shadowsoffire.apotheosis.compat.jei.AdventureJEIPlugin$GemSubtypes", remap = false)
public abstract class JeiGemSubtypeMixin {
    @Inject(method = "apply", at = @At("TAIL"), cancellable = true, remap = false, require = 1, allow = 1)
    private void apothicAscension$extendSubtypeKey(
        ItemStack stack,
        @Coerce Object context,
        CallbackInfoReturnable<String> cir
    ) {
        int tier = GemTierData.get(stack);
        if (tier <= 0) return;
        String base = cir.getReturnValue();
        if (base == null) base = "apotheosis:gem";
        cir.setReturnValue(base + "|apothic_ascension_tier=" + tier);
    }
}
