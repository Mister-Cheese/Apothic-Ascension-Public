// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.compat;

import dev.mistercheese.apothicascension.ApothicAscension;
import java.util.Objects;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Generic Beta 14 equipment-state boundary.
 *
 * <p>AA and Apotheosis may mutate components in their own namespaces during reforging. Every
 * component owned by Minecraft or another mod is treated as foreign state and must survive with
 * an equal value. Unknown or unregistered component types are foreign by default.</p>
 */
public final class EquipmentInteropPolicy {
    public static boolean preservesForeignState(ItemStack before, ItemStack after) {
        if (before == null || before.isEmpty() || after == null || after.isEmpty()) return false;
        if (before.getItem() != after.getItem()) return false;

        for (TypedDataComponent<?> component : before.getComponents()) {
            if (isOwnedMutation(component.type())) continue;
            if (!Objects.equals(component.value(), after.get(component.type()))) return false;
        }
        return true;
    }

    public static boolean isOwnedMutation(DataComponentType<?> type) {
        if (type == null) return false;
        ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        if (id == null) return false;
        String namespace = id.getNamespace();
        return ApothicAscension.MODID.equals(namespace) || "apotheosis".equals(namespace);
    }

    private EquipmentInteropPolicy() {}
}
