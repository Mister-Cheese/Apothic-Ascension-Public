// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.menu;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Input slot that rejects unrelated items and can be transaction-locked while Apex is processing. */
final class FilteredInputSlot extends Slot {
    private final Predicate<ItemStack> admission;
    private final BooleanSupplier locked;

    FilteredInputSlot(Container container, int slot, int x, int y, Predicate<ItemStack> admission, BooleanSupplier locked) {
        super(container, slot, x, y);
        this.admission = admission;
        this.locked = locked;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !this.locked.getAsBoolean() && this.admission.test(stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return !this.locked.getAsBoolean() && super.mayPickup(player);
    }
}
