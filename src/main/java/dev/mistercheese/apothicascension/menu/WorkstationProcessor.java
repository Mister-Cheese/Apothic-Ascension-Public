// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.menu;

import dev.mistercheese.apothicascension.AscensionRarity;
import dev.mistercheese.apothicascension.GemTier;
import dev.mistercheese.apothicascension.GemTierData;
import dev.mistercheese.apothicascension.registry.ModItems;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Pure workstation recipe/transaction logic shared by menus and runtime qualification. */
public final class WorkstationProcessor {
    public static final int INPUT_COUNT = 3;

    public static Operation preview(WorkstationKind kind, Container input, int loomFamily) {
        if (input == null || input.getContainerSize() < INPUT_COUNT) return Operation.EMPTY;
        return switch (kind) {
            case ASCENSION_FORGE -> forge(input);
            case GEM_RESONATOR -> resonator(input);
            case AFFIX_LOOM -> loom(input, loomFamily);
            case APEX_ASCENSION_BENCH -> apex(input);
        };
    }

    public static boolean consume(Operation operation, Container input) {
        if (operation == null || operation.output().isEmpty() || input == null || input.getContainerSize() < INPUT_COUNT) return false;
        if (!operation.matchesInputs(input)) return false;
        int[] costs = operation.costs();
        for (int i = 0; i < INPUT_COUNT; i++) {
            if (costs[i] < 0 || input.getItem(i).getCount() < costs[i]) return false;
        }
        // Validation above is deliberately complete before the first mutation. Container removal is
        // therefore all-or-nothing with respect to AA's three-slot workstation contract.
        for (int i = 0; i < INPUT_COUNT; i++) {
            if (costs[i] > 0) input.removeItem(i, costs[i]);
        }
        input.setChanged();
        return true;
    }

    private static Operation forge(Container input) {
        ItemStack primary = input.getItem(0);
        ItemStack core = input.getItem(1);
        ItemStack secondary = input.getItem(2);
        if (core.getItem() != ModItems.ASCENSION_CORE.get()) return Operation.EMPTY;

        AscensionRarity rarity = ModItems.rarityForMaterial(primary);
        if (rarity != null && primary.getCount() >= 4 && secondary.isEmpty()) {
            AscensionRarity next = AscensionRarity.byRank(rarity.rank() + 1);
            if (next == null || isApexTier(next)) return Operation.EMPTY;
            return operation(input, new ItemStack(ModItems.materialFor(next).get()), new int[] {4, 0, 0});
        }

        if (primary.getItem() == ModItems.TRANSCENDENT_MATERIAL.get()
            && primary.getCount() >= 2
            && secondary.getItem() == ModItems.STABILITY_THREAD.get()
            && secondary.getCount() >= 4) {
            return operation(input, new ItemStack(ModItems.EVOLUTION_CATALYST.get()), new int[] {2, 0, 4});
        }
        return Operation.EMPTY;
    }

    private static Operation resonator(Container input) {
        ItemStack gem = input.getItem(0);
        ItemStack material = input.getItem(1);
        ItemStack matrix = input.getItem(2);
        if (!(gem.getItem() instanceof GemItem)) return Operation.EMPTY;
        if (GemItem.getPurity(gem) != Purity.PERFECT) return Operation.EMPTY;
        if (matrix.getItem() != ModItems.GEM_ASCENSION_MATRIX.get()) return Operation.EMPTY;

        int current = GemTierData.get(gem);
        if (current >= GemTier.APOTHEOTIC.id()) return Operation.EMPTY;
        GemTier next = GemTier.byId(current).next();
        AscensionRarity nextRarity = AscensionRarity.byId(next.key());
        if (nextRarity == null || isApexTier(nextRarity) || material.getItem() != ModItems.materialFor(nextRarity).get()) return Operation.EMPTY;

        ItemStack output = gem.copy();
        output.setCount(1);
        GemTierData.set(output, next.id());
        return operation(input, output, new int[] {1, 1, 0});
    }

    private static Operation apex(Container input) {
        ItemStack primary = input.getItem(0);
        ItemStack catalyst = input.getItem(1);
        ItemStack anchor = input.getItem(2);

        AscensionRarity rarity = ModItems.rarityForMaterial(primary);
        if (rarity != null && primary.getCount() >= 4 && catalyst.getItem() == ModItems.ASCENSION_CORE.get() && anchor.isEmpty()) {
            AscensionRarity next = AscensionRarity.byRank(rarity.rank() + 1);
            if (next != null && isApexTier(next)) {
                return operation(input, new ItemStack(ModItems.materialFor(next).get()), new int[] {4, 0, 0});
            }
        }

        if (primary.getItem() instanceof GemItem && GemItem.getPurity(primary) == Purity.PERFECT
            && anchor.getItem() == ModItems.GEM_ASCENSION_MATRIX.get()) {
            int current = GemTierData.get(primary);
            if (current < GemTier.APOTHEOTIC.id()) {
                GemTier next = GemTier.byId(current).next();
                AscensionRarity nextRarity = AscensionRarity.byId(next.key());
                if (nextRarity != null && isApexTier(nextRarity)
                    && catalyst.getItem() == ModItems.materialFor(nextRarity).get()) {
                    ItemStack output = primary.copy();
                    output.setCount(1);
                    GemTierData.set(output, next.id());
                    return operation(input, output, new int[] {1, 1, 0});
                }
            }
        }
        return Operation.EMPTY;
    }

    private static boolean isApexTier(AscensionRarity rarity) {
        return rarity == AscensionRarity.TRANSCENDENT || rarity == AscensionRarity.APOTHEOTIC;
    }

    private static Operation loom(Container input, int loomFamily) {
        ItemStack thread = input.getItem(0);
        ItemStack catalyst = input.getItem(1);
        ItemStack core = input.getItem(2);
        if (thread.getItem() != ModItems.STABILITY_THREAD.get() || thread.getCount() < 4) return Operation.EMPTY;
        if (catalyst.getItem() != ModItems.EVOLUTION_CATALYST.get()) return Operation.EMPTY;
        if (core.getItem() != ModItems.ASCENSION_CORE.get()) return Operation.EMPTY;
        if (loomFamily < 0 || loomFamily >= 5) return Operation.EMPTY;
        return operation(input, new ItemStack(ModItems.focusSigil(loomFamily).get()), new int[] {4, 1, 0});
    }

    private static Operation operation(Container input, ItemStack output, int[] costs) {
        ItemStack[] expectedInputs = new ItemStack[INPUT_COUNT];
        for (int i = 0; i < INPUT_COUNT; i++) expectedInputs[i] = oneCopy(input.getItem(i));
        return new Operation(output, costs, expectedInputs);
    }

    private static ItemStack oneCopy(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    /**
     * Immutable-by-interface transaction preview. The stored source snapshots intentionally ignore
     * counts while retaining item identity and all data components; costs enforce the minimum count.
     * This permits adding more of the same input between preview and commit, but rejects replacement
     * items, component mutation, or removal of a required reusable/zero-cost catalyst.
     */
    public record Operation(ItemStack output, int[] costs, ItemStack[] expectedInputs) {
        public static final Operation EMPTY = new Operation(
            ItemStack.EMPTY,
            new int[] {0, 0, 0},
            new ItemStack[] {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY}
        );

        public Operation {
            output = output == null || output.isEmpty() ? ItemStack.EMPTY : output.copy();
            costs = costs == null ? new int[] {0, 0, 0} : costs.clone();
            if (costs.length != INPUT_COUNT) throw new IllegalArgumentException("workstation operation requires exactly three costs");
            if (expectedInputs == null || expectedInputs.length != INPUT_COUNT) {
                throw new IllegalArgumentException("workstation operation requires exactly three input snapshots");
            }
            ItemStack[] snapshots = new ItemStack[INPUT_COUNT];
            for (int i = 0; i < INPUT_COUNT; i++) snapshots[i] = oneCopy(expectedInputs[i]);
            expectedInputs = snapshots;
        }

        @Override
        public ItemStack output() {
            return this.output.isEmpty() ? ItemStack.EMPTY : this.output.copy();
        }

        @Override
        public int[] costs() {
            return this.costs.clone();
        }

        @Override
        public ItemStack[] expectedInputs() {
            ItemStack[] copy = new ItemStack[INPUT_COUNT];
            for (int i = 0; i < INPUT_COUNT; i++) copy[i] = oneCopy(this.expectedInputs[i]);
            return copy;
        }

        public boolean matchesInputs(Container input) {
            if (input == null || input.getContainerSize() < INPUT_COUNT) return false;
            for (int i = 0; i < INPUT_COUNT; i++) {
                ItemStack expected = this.expectedInputs[i];
                ItemStack current = input.getItem(i);
                if (expected.isEmpty() || current.isEmpty()) {
                    if (!(expected.isEmpty() && current.isEmpty())) return false;
                }
                else if (!ItemStack.isSameItemSameComponents(expected, current)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Returns whether another preview describes the same delayed transaction contract.
         * Output and costs alone are insufficient: two different source stacks can legitimately
         * produce the same result, so timed workstations must also bind the preview to the exact
         * component-aware source identities captured when processing began.
         */
        public boolean sameContract(Operation other) {
            if (other == null) return false;
            if (this.output.getCount() != other.output.getCount()
                || !ItemStack.isSameItemSameComponents(this.output, other.output)
                || !java.util.Arrays.equals(this.costs, other.costs)) {
                return false;
            }
            for (int i = 0; i < INPUT_COUNT; i++) {
                ItemStack left = this.expectedInputs[i];
                ItemStack right = other.expectedInputs[i];
                if (left.isEmpty() || right.isEmpty()) {
                    if (!(left.isEmpty() && right.isEmpty())) return false;
                }
                else if (!ItemStack.isSameItemSameComponents(left, right)) {
                    return false;
                }
            }
            return true;
        }
    }

    private WorkstationProcessor() {}
}
