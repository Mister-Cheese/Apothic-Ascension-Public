// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.menu;

import dev.mistercheese.apothicascension.block.entity.ApexBenchBlockEntity;
import dev.mistercheese.apothicascension.registry.ModBlocks;
import dev.mistercheese.apothicascension.registry.ModMenus;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Shared workstation menu. Forge/Resonator/Loom remain ephemeral preview transactions; Apex is backed
 * by the persistent block-entity inventory and exposes server-owned process/fade state through DataSlots.
 *
 * <p>The five Apex reforge candidates are virtual read-only menu slots. They never live in the block
 * entity and cannot be extracted. The server regenerates them only when the source gear/material/sigil
 * identity or the server-owned reforge seed changes, then normal container synchronization carries the
 * resulting ItemStacks to the client.</p>
 */
public final class AscensionWorkstationMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOTS = 3;
    public static final int RESULT_SLOT = 3;
    public static final int APEX_OFFER_START = 4;
    public static final int APEX_OFFER_COUNT = ApexReforgeOffers.OFFER_COUNT;
    public static final int APEX_BUTTON_BASE = 100;
    /** Non-Apex player inventory start retained as a stable public constant. */
    public static final int PLAYER_START = 4;
    public static final int PLAYER_END = PLAYER_START + 36;

    private final WorkstationKind kind;
    private final ContainerLevelAccess access;
    private final Container inputs;
    private final Container result;
    private final Container apexOffers;
    private final Player menuPlayer;
    private final int resultIndex;
    private final boolean persistentApex;
    private final DataSlot loomFamily;
    private final DataSlot apexProgress;
    private final DataSlot apexDuration;
    private final DataSlot apexFade;

    private ItemStack lastOfferInput = ItemStack.EMPTY;
    private ItemStack lastOfferMaterial = ItemStack.EMPTY;
    private ItemStack lastOfferSigil = ItemStack.EMPTY;
    private int lastOfferSeed = Integer.MIN_VALUE;
    private boolean lastOfferValid;

    private AscensionWorkstationMenu(
        MenuType<?> type,
        int containerId,
        Inventory playerInventory,
        WorkstationKind kind,
        ContainerLevelAccess access,
        Container inputs,
        Container result,
        int resultIndex,
        boolean persistentApex,
        DataSlot loomFamily,
        DataSlot apexProgress,
        DataSlot apexDuration,
        DataSlot apexFade
    ) {
        super(type, containerId);
        this.kind = kind;
        this.access = access;
        this.inputs = inputs;
        this.result = result;
        this.resultIndex = resultIndex;
        this.persistentApex = persistentApex;
        this.apexOffers = persistentApex ? new SimpleContainer(APEX_OFFER_COUNT) : new SimpleContainer(0);
        this.menuPlayer = playerInventory.player;
        this.loomFamily = loomFamily;
        this.apexProgress = apexProgress;
        this.apexDuration = apexDuration;
        this.apexFade = apexFade;
        this.addDataSlot(this.loomFamily);
        this.addDataSlot(this.apexProgress);
        this.addDataSlot(this.apexDuration);
        this.addDataSlot(this.apexFade);

        if (!persistentApex && inputs instanceof SimpleContainer simple) simple.addListener(this::slotsChanged);

        addWorkstationSlots();
        addPlayerInventory(playerInventory);
        this.refreshResult();
    }

    public static AscensionWorkstationMenu client(WorkstationKind kind, int containerId, Inventory inventory) {
        DataSlot family = DataSlot.standalone();
        family.set(0);
        DataSlot progress = DataSlot.standalone();
        DataSlot duration = DataSlot.standalone();
        DataSlot fade = DataSlot.standalone();
        duration.set(18);
        if (kind == WorkstationKind.APEX_ASCENSION_BENCH) {
            SimpleContainer apex = new SimpleContainer(ApexBenchBlockEntity.SLOT_COUNT);
            return new AscensionWorkstationMenu(ModMenus.typeFor(kind), containerId, inventory, kind, ContainerLevelAccess.NULL,
                apex, apex, ApexBenchBlockEntity.RESULT_SLOT, true, family, progress, duration, fade);
        }
        SimpleContainer input = new SimpleContainer(INPUT_SLOTS);
        return new AscensionWorkstationMenu(ModMenus.typeFor(kind), containerId, inventory, kind, ContainerLevelAccess.NULL,
            input, new ResultContainer(), 0, false, family, progress, duration, fade);
    }

    public static AscensionWorkstationMenu server(
        WorkstationKind kind,
        int containerId,
        Inventory inventory,
        ContainerLevelAccess access
    ) {
        if (kind == WorkstationKind.APEX_ASCENSION_BENCH) {
            throw new IllegalArgumentException("Apex menus require the owning ApexBenchBlockEntity");
        }
        DataSlot family = DataSlot.standalone();
        family.set(0);
        return new AscensionWorkstationMenu(ModMenus.typeFor(kind), containerId, inventory, kind, access,
            new SimpleContainer(INPUT_SLOTS), new ResultContainer(), 0, false,
            family, zeroSlot(), zeroSlot(), zeroSlot());
    }

    public static AscensionWorkstationMenu serverApex(
        int containerId,
        Inventory inventory,
        ContainerLevelAccess access,
        ApexBenchBlockEntity bench
    ) {
        return new AscensionWorkstationMenu(
            ModMenus.APEX_ASCENSION_BENCH.get(), containerId, inventory, WorkstationKind.APEX_ASCENSION_BENCH, access,
            bench, bench, ApexBenchBlockEntity.RESULT_SLOT, true, zeroSlot(),
            readOnlySlot(bench::processTicks), readOnlySlot(bench::processDuration), readOnlySlot(bench::processFadeTicks)
        );
    }

    private static DataSlot zeroSlot() {
        return readOnlySlot(() -> 0);
    }

    private static DataSlot readOnlySlot(java.util.function.IntSupplier supplier) {
        return new DataSlot() {
            @Override public int get() { return supplier.getAsInt(); }
            @Override public void set(int value) { }
        };
    }

    private void addWorkstationSlots() {
        int[][] inputPos;
        int[] resultPos;
        if (this.kind == WorkstationKind.APEX_ASCENSION_BENCH) {
            inputPos = new int[][] {{29, 48}, {29, 76}, {29, 104}};
            resultPos = new int[] {156, 105};
        }
        else {
            inputPos = new int[][] {{32, 43}, {59, 43}, {86, 43}};
            resultPos = new int[] {147, 43};
        }

        BooleanSupplier locked = () -> this.kind == WorkstationKind.APEX_ASCENSION_BENCH && this.apexProgress.get() > 0;
        for (int i = 0; i < INPUT_SLOTS; i++) {
            final int slot = i;
            this.addSlot(new FilteredInputSlot(this.inputs, slot, inputPos[i][0], inputPos[i][1],
                stack -> WorkstationInputRules.accepts(this.kind, slot, stack), locked));
        }

        this.addSlot(new Slot(this.result, this.resultIndex, resultPos[0], resultPos[1]) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }

            @Override
            public boolean mayPickup(Player player) {
                if (AscensionWorkstationMenu.this.persistentApex) {
                    return AscensionWorkstationMenu.this.apexProgress.get() <= 0 && super.mayPickup(player);
                }
                WorkstationProcessor.Operation operation = WorkstationProcessor.preview(
                    AscensionWorkstationMenu.this.kind,
                    AscensionWorkstationMenu.this.inputs,
                    AscensionWorkstationMenu.this.loomFamily.get()
                );
                ItemStack displayed = this.getItem();
                ItemStack current = operation.output();
                return !displayed.isEmpty()
                    && !current.isEmpty()
                    && displayed.getCount() == current.getCount()
                    && ItemStack.isSameItemSameComponents(displayed, current)
                    && operation.matchesInputs(AscensionWorkstationMenu.this.inputs)
                    && super.mayPickup(player);
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                if (!AscensionWorkstationMenu.this.persistentApex) {
                    WorkstationProcessor.Operation operation = WorkstationProcessor.preview(
                        AscensionWorkstationMenu.this.kind,
                        AscensionWorkstationMenu.this.inputs,
                        AscensionWorkstationMenu.this.loomFamily.get()
                    );
                    if (!player.level().isClientSide && WorkstationProcessor.consume(operation, AscensionWorkstationMenu.this.inputs)) {
                        AscensionWorkstationMenu.this.refreshResult();
                    }
                }
                super.onTake(player, stack);
            }
        });

        if (this.persistentApex) {
            for (int i = 0; i < APEX_OFFER_COUNT; i++) {
                this.addSlot(new Slot(this.apexOffers, i, 92 + i * 32, 60) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                    @Override public boolean mayPickup(Player player) { return false; }
                });
            }
        }
    }

    public WorkstationKind kind() { return this.kind; }
    public int selectedFamily() { return this.loomFamily.get(); }
    public int apexProgress() { return this.apexProgress.get(); }
    public int apexDuration() { return Math.max(1, this.apexDuration.get()); }
    public int apexFadeTicks() { return this.apexFade.get(); }
    public boolean apexProcessing() { return this.kind == WorkstationKind.APEX_ASCENSION_BENCH && this.apexProgress.get() > 0; }
    public int playerStart() { return this.persistentApex ? APEX_OFFER_START + APEX_OFFER_COUNT : PLAYER_START; }
    public int playerEnd() { return this.playerStart() + 36; }
    public int apexOfferSlot(int choice) { return APEX_OFFER_START + choice; }
    public boolean hasApexOffers() {
        if (!this.persistentApex) return false;
        for (int i = 0; i < APEX_OFFER_COUNT; i++) if (!this.apexOffers.getItem(i).isEmpty()) return true;
        return false;
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (!this.persistentApex) this.refreshResult();
    }

    private void refreshResult() {
        if (this.persistentApex) {
            this.broadcastChanges();
            return;
        }
        WorkstationProcessor.Operation operation = WorkstationProcessor.preview(this.kind, this.inputs, this.loomFamily.get());
        this.result.setItem(this.resultIndex, operation.output().copy());
        this.broadcastChanges();
    }

    @Override
    public void broadcastChanges() {
        if (this.persistentApex) this.refreshApexOffersIfNeeded();
        super.broadcastChanges();
    }

    private void refreshApexOffersIfNeeded() {
        if (this.menuPlayer == null || this.menuPlayer.level().isClientSide) return;
        ItemStack input = this.inputs.getItem(0);
        ItemStack material = this.inputs.getItem(1);
        ItemStack sigil = this.inputs.getItem(2);
        boolean valid = ApexReforgeOffers.isReforgeInput(input, material, sigil);
        int seed = valid && this.inputs instanceof ApexBenchBlockEntity bench ? bench.reforgeSeed() : 0;
        boolean changed = valid != this.lastOfferValid
            || seed != this.lastOfferSeed
            || !sameOfferSource(this.lastOfferInput, input)
            || !sameOfferSource(this.lastOfferMaterial, material)
            || !sameOfferSource(this.lastOfferSigil, sigil);
        if (!changed) return;

        if (valid) {
            List<ItemStack> offers = ApexReforgeOffers.generate(this.menuPlayer, input, material, seed);
            for (int i = 0; i < APEX_OFFER_COUNT; i++) this.apexOffers.setItem(i, offers.get(i).copy());
        }
        else {
            this.apexOffers.clearContent();
        }
        this.lastOfferInput = oneCopy(input);
        this.lastOfferMaterial = oneCopy(material);
        this.lastOfferSigil = oneCopy(sigil);
        this.lastOfferSeed = seed;
        this.lastOfferValid = valid;
    }

    private static boolean sameOfferSource(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty();
        return ItemStack.isSameItemSameComponents(a, b);
    }

    private static ItemStack oneCopy(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.kind == WorkstationKind.AFFIX_LOOM && id >= 0 && id < 5) {
            this.loomFamily.set(id);
            this.refreshResult();
            return true;
        }
        if (this.persistentApex && id >= APEX_BUTTON_BASE && id < APEX_BUTTON_BASE + APEX_OFFER_COUNT) {
            if (player instanceof ServerPlayer serverPlayer && this.inputs instanceof ApexBenchBlockEntity bench) {
                boolean started = bench.startReforge(serverPlayer, id - APEX_BUTTON_BASE);
                if (started) this.broadcastChanges();
                return started;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        // Persistent Apex menus must remain attached to the exact owning block entity. A generic
        // block-type check would allow a break/re-place at the same coordinates to leave this menu
        // operating on the discarded old inventory until the next close.
        if (this.persistentApex && this.inputs instanceof ApexBenchBlockEntity bench) {
            return bench.stillValid(player);
        }
        return stillValid(this.access, player, ModBlocks.blockFor(this.kind));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide && !this.persistentApex) this.clearContainer(player, this.inputs);
        if (!this.persistentApex) this.result.clearContent();
        if (this.persistentApex) this.apexOffers.clearContent();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerStart = this.playerStart();
        int playerEnd = this.playerEnd();

        if (index == RESULT_SLOT) {
            if (!slot.mayPickup(player) || !this.moveItemStackTo(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        }
        else if (index < playerStart) {
            if (!slot.mayPickup(player) || !this.moveItemStackTo(stack, playerStart, playerEnd, false)) return ItemStack.EMPTY;
        }
        else {
            if (this.apexProcessing()) return ItemStack.EMPTY;
            boolean moved = false;
            for (int target = 0; target < INPUT_SLOTS && !stack.isEmpty(); target++) {
                Slot input = this.slots.get(target);
                if (!input.mayPlace(stack)) continue;
                if (this.moveItemStackTo(stack, target, target + 1, false)) moved = true;
            }
            if (!moved) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    private void addPlayerInventory(Inventory inventory) {
        int x0 = this.persistentApex ? 55 : 17;
        int y0 = this.persistentApex ? 145 : 107;
        int hotbarY = this.persistentApex ? 203 : 165;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, x0 + col * 18, y0 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) this.addSlot(new Slot(inventory, col, x0 + col * 18, hotbarY));
    }
}
