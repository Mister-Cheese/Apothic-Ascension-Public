// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.block.entity;

import dev.mistercheese.apothicascension.block.ApexMultiblock;
import dev.mistercheese.apothicascension.config.AscensionClientConfig;
import dev.mistercheese.apothicascension.config.AscensionServerConfig;
import dev.mistercheese.apothicascension.menu.ApexReforgeOffers;
import dev.mistercheese.apothicascension.menu.WorkstationKind;
import dev.mistercheese.apothicascension.menu.WorkstationInputRules;
import dev.mistercheese.apothicascension.menu.WorkstationProcessor;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.placebo.util.EnchantmentUtils;
import dev.mistercheese.apothicascension.registry.ModBlockEntities;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent Apex inventory, server-authoritative timed processing, and client seal animation anchor. */
public final class ApexBenchBlockEntity extends BlockEntity implements Container {
    public static final int INPUT_SLOTS = 3;
    public static final int RESULT_SLOT = 3;
    public static final int SLOT_COUNT = 4;

    public static final int EVENT_ACTIVATE = 1;
    public static final int EVENT_DEACTIVATE = 2;
    public static final int EVENT_PROCESS_START = 3;
    public static final int EVENT_PROCESS_COMPLETE = 4;
    public static final int EVENT_PROCESS_CANCEL = 5;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    private boolean serverInitialized;
    private boolean serverValid;
    private boolean freshPlacement;
    private int processTicks;
    private int processFadeTicks;
    private WorkstationProcessor.Operation pendingWorkstationOperation = WorkstationProcessor.Operation.EMPTY;
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private int[] pendingCosts = new int[] {0, 0, 0};
    private int pendingReforgeChoice = -1;
    private UUID pendingReforgePlayer;
    private ItemStack pendingReforgeInput = ItemStack.EMPTY;
    private ItemStack pendingReforgeMaterial = ItemStack.EMPTY;
    private ItemStack pendingReforgeSigils = ItemStack.EMPTY;
    private int pendingReforgeSeed;
    private int pendingReforgeExperiencePoints;
    private int reforgeSeed;

    private boolean clientInitialized;
    private boolean clientStructureValid;
    private int clientSealTicks;
    private int previousClientSealTicks;
    private boolean clientProcessing;
    private int clientProcessTicks;
    private int previousClientProcessTicks;
    private int clientProcessDuration = 1;
    private int clientProcessFadeTicks;
    private int previousClientProcessFadeTicks;
    private int clientProximityTicks;
    private int previousClientProximityTicks;
    private boolean clientPlayerNearby;

    public ApexBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.APEX_ASCENSION_BENCH.get(), pos, state);
    }

    public void markFreshPlacement() {
        this.freshPlacement = true;
    }

    /** Server-owned snapshot used when block removal prevents another structure-transition tick. */
    public boolean serverStructureActive() {
        return this.serverInitialized && this.serverValid;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ApexBenchBlockEntity bench) {
        boolean valid = ApexMultiblock.isValid(level, pos);
        if (!bench.serverInitialized) {
            bench.serverInitialized = true;
            bench.serverValid = valid;
            if (valid && bench.freshPlacement) bench.activateServer(level, pos, state);
            bench.freshPlacement = false;
        }
        else {
            if (valid && !bench.serverValid) bench.activateServer(level, pos, state);
            else if (!valid && bench.serverValid) bench.deactivateServer(level, pos, state);
            bench.serverValid = valid;
        }

        if (bench.processFadeTicks > 0) bench.processFadeTicks--;
        if (!valid) {
            bench.cancelProcessing(level, pos, state, true);
            return;
        }
        bench.tickProcessing(level, pos, state);
    }

    private void activateServer(Level level, BlockPos pos, BlockState state) {
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.72F, 1.0F);
        level.blockEvent(pos, state.getBlock(), EVENT_ACTIVATE, 0);
    }

    private void deactivateServer(Level level, BlockPos pos, BlockState state) {
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.72F, 1.0F);
        level.blockEvent(pos, state.getBlock(), EVENT_DEACTIVATE, 0);
    }

    private void tickProcessing(Level level, BlockPos pos, BlockState state) {
        if (!this.getItem(RESULT_SLOT).isEmpty()) {
            this.cancelProcessing(level, pos, state, false);
            return;
        }

        if (this.pendingReforgeChoice >= 0) {
            this.tickReforgeProcessing(level, pos, state);
            return;
        }

        WorkstationProcessor.Operation operation = WorkstationProcessor.preview(WorkstationKind.APEX_ASCENSION_BENCH, this, 0);
        if (operation.output().isEmpty()) {
            this.cancelProcessing(level, pos, state, false);
            return;
        }

        if (this.processTicks <= 0) {
            this.pendingWorkstationOperation = operation;
            this.pendingOutput = operation.output().copy();
            this.pendingCosts = operation.costs();
            this.processTicks = 1;
            int duration = AscensionServerConfig.apexProcessTicks();
            level.blockEvent(pos, state.getBlock(), EVENT_PROCESS_START, duration);
            this.setChanged();
            return;
        }

        if (!sameOperation(operation)) {
            this.cancelProcessing(level, pos, state, true);
            return;
        }

        int duration = AscensionServerConfig.apexProcessTicks();
        if (this.processTicks < duration) {
            this.processTicks++;
            this.setChanged();
            return;
        }

        WorkstationProcessor.Operation finalOperation = WorkstationProcessor.preview(WorkstationKind.APEX_ASCENSION_BENCH, this, 0);
        if (!sameOperation(finalOperation) || !WorkstationProcessor.consume(finalOperation, this)) {
            this.cancelProcessing(level, pos, state, true);
            return;
        }

        this.setItem(RESULT_SLOT, finalOperation.output().copy());
        this.clearPendingProcess();
        this.processFadeTicks = 6;
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.48F, 1.28F);
        level.blockEvent(pos, state.getBlock(), EVENT_PROCESS_COMPLETE, this.processFadeTicks);
        this.setChanged();
    }


    /**
     * Begins a five-choice Apex reforge using only server-generated offers and server-owned costs.
     * The client sends an offer index, never an output stack or cost.
     */
    public boolean startReforge(ServerPlayer player, int choice) {
        if (this.level == null || this.level.isClientSide || choice < 0 || choice >= ApexReforgeOffers.OFFER_COUNT) return false;
        if (this.processTicks > 0 || this.pendingReforgeChoice >= 0 || !this.getItem(RESULT_SLOT).isEmpty()) return false;
        if (!ApexMultiblock.isValid(this.level, this.worldPosition)) return false;

        ItemStack input = this.getItem(0);
        ItemStack material = this.getItem(1);
        ItemStack sigils = this.getItem(2);
        if (!ApexReforgeOffers.isReforgeInput(input, material, sigils)) return false;
        if (!ApexReforgeOffers.canAfford(player, material, sigils, choice)) return false;

        int seed = this.reforgeSeed();
        List<ItemStack> offers = ApexReforgeOffers.generate(player, input, material, seed);
        if (choice >= offers.size()) return false;
        ItemStack selected = offers.get(choice);
        if (selected.isEmpty()) return false;
        ApexReforgeOffers.Cost cost = ApexReforgeOffers.cost(material, choice);
        if (!cost.valid()) return false;

        this.pendingOutput = selected.copy();
        this.pendingCosts = new int[] {1, cost.materials(), cost.sigils()};
        this.pendingReforgeChoice = choice;
        this.pendingReforgePlayer = player.getUUID();
        this.pendingReforgeInput = input.copy();
        this.pendingReforgeInput.setCount(1);
        this.pendingReforgeMaterial = material.copy();
        this.pendingReforgeMaterial.setCount(1);
        this.pendingReforgeSigils = sigils.copy();
        this.pendingReforgeSigils.setCount(1);
        this.pendingReforgeSeed = seed;
        this.pendingReforgeExperiencePoints = cost.experiencePoints();
        this.processTicks = 1;
        int duration = AscensionServerConfig.apexProcessTicks();
        this.level.blockEvent(this.worldPosition, this.getBlockState().getBlock(), EVENT_PROCESS_START, duration);
        this.setChanged();
        return true;
    }

    private void tickReforgeProcessing(Level level, BlockPos pos, BlockState state) {
        int duration = AscensionServerConfig.apexProcessTicks();
        if (this.processTicks < duration) {
            this.processTicks++;
            this.setChanged();
            return;
        }

        ServerPlayer player = this.pendingReforgePlayer == null || level.getServer() == null
            ? null : level.getServer().getPlayerList().getPlayer(this.pendingReforgePlayer);
        if (player == null || !this.validatePendingReforge(player)) {
            this.cancelProcessing(level, pos, state, true);
            return;
        }

        this.removeItem(0, 1);
        if (!player.isCreative()) {
            this.removeItem(1, this.pendingCosts[1]);
            this.removeItem(2, this.pendingCosts[2]);
            EnchantmentUtils.chargeExperience(player, this.pendingReforgeExperiencePoints);
        }
        this.setItem(RESULT_SLOT, this.pendingOutput.copy());
        this.rerollReforgeSeed();
        this.clearPendingProcess();
        this.processFadeTicks = 6;
        level.playSound(null, pos, Apoth.Sounds.REFORGE_ITEM_REFORGED.value(), SoundSource.BLOCKS, 0.40F,
            0.96F + level.random.nextFloat() * 0.10F);
        level.blockEvent(pos, state.getBlock(), EVENT_PROCESS_COMPLETE, this.processFadeTicks);
        this.setChanged();
    }

    private boolean validatePendingReforge(ServerPlayer player) {
        if (this.pendingReforgeChoice < 0 || this.pendingOutput.isEmpty() || this.pendingReforgePlayer == null) return false;
        if (this.level == null || player.level() != this.level) return false;
        if (this.reforgeSeed() != this.pendingReforgeSeed) return false;
        ItemStack input = this.getItem(0);
        ItemStack material = this.getItem(1);
        ItemStack sigils = this.getItem(2);
        if (input.isEmpty() || !ItemStack.isSameItemSameComponents(this.pendingReforgeInput, input)) return false;
        if (material.isEmpty() || !ItemStack.isSameItemSameComponents(this.pendingReforgeMaterial, material)) return false;
        if (sigils.isEmpty() || !ItemStack.isSameItemSameComponents(this.pendingReforgeSigils, sigils)) return false;
        if (!ApexReforgeOffers.isReforgeInput(input, material, sigils)) return false;
        ApexReforgeOffers.Cost cost = ApexReforgeOffers.cost(material, this.pendingReforgeChoice);
        if (!cost.valid() || cost.materials() != this.pendingCosts[1] || cost.sigils() != this.pendingCosts[2]
            || cost.experiencePoints() != this.pendingReforgeExperiencePoints) return false;
        if (!ApexReforgeOffers.canAfford(player, material, sigils, this.pendingReforgeChoice)) return false;

        // Recompute the server-owned selected offer at commit time. If a datapack reload, world-tier
        // transition, Luck change, or other legitimate server-side context change altered the offer,
        // cancel losslessly instead of committing an output generated from stale pre-reload state.
        List<ItemStack> currentOffers = ApexReforgeOffers.generate(player, input, material, this.pendingReforgeSeed);
        if (this.pendingReforgeChoice >= currentOffers.size()) return false;
        ItemStack current = currentOffers.get(this.pendingReforgeChoice);
        return !current.isEmpty()
            && current.getCount() == this.pendingOutput.getCount()
            && ItemStack.isSameItemSameComponents(current, this.pendingOutput);
    }

    /** Machine-owned deterministic reforge seed; persisted with the bench, never in raw player NBT. */
    public int reforgeSeed() {
        if (this.reforgeSeed == 0) {
            int seed = this.level != null ? this.level.random.nextInt() : this.worldPosition.hashCode() ^ 0x5EED4A11;
            this.reforgeSeed = seed == 0 ? 1 : seed;
            this.setChanged();
        }
        return this.reforgeSeed;
    }

    private void rerollReforgeSeed() {
        int previous = this.reforgeSeed;
        int next;
        do next = this.level != null ? this.level.random.nextInt() : previous * 1103515245 + 12345;
        while (next == 0 || next == previous);
        this.reforgeSeed = next;
        this.setChanged();
    }

    private void clearPendingProcess() {
        this.processTicks = 0;
        this.pendingWorkstationOperation = WorkstationProcessor.Operation.EMPTY;
        this.pendingOutput = ItemStack.EMPTY;
        this.pendingCosts = new int[] {0, 0, 0};
        this.pendingReforgeChoice = -1;
        this.pendingReforgePlayer = null;
        this.pendingReforgeInput = ItemStack.EMPTY;
        this.pendingReforgeMaterial = ItemStack.EMPTY;
        this.pendingReforgeSigils = ItemStack.EMPTY;
        this.pendingReforgeSeed = 0;
        this.pendingReforgeExperiencePoints = 0;
    }

    private boolean sameOperation(WorkstationProcessor.Operation operation) {
        return this.pendingWorkstationOperation != WorkstationProcessor.Operation.EMPTY
            && this.pendingWorkstationOperation.sameContract(operation);
    }

    private void cancelProcessing(Level level, BlockPos pos, BlockState state, boolean notifyClient) {
        boolean hasPendingState = this.processTicks > 0
            || this.pendingWorkstationOperation != WorkstationProcessor.Operation.EMPTY
            || !this.pendingOutput.isEmpty()
            || this.pendingReforgeChoice >= 0
            || this.pendingReforgePlayer != null
            || !this.pendingReforgeInput.isEmpty()
            || !this.pendingReforgeMaterial.isEmpty()
            || !this.pendingReforgeSigils.isEmpty();
        if (!hasPendingState) return;
        this.clearPendingProcess();
        if (notifyClient) level.blockEvent(pos, state.getBlock(), EVENT_PROCESS_CANCEL, 3);
        this.setChanged();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ApexBenchBlockEntity bench) {
        boolean valid = ApexMultiblock.isValid(level, pos);
        int duration = Math.max(1, AscensionClientConfig.apexActivationTicks());
        bench.previousClientSealTicks = bench.clientSealTicks;
        bench.previousClientProcessTicks = bench.clientProcessTicks;
        bench.previousClientProcessFadeTicks = bench.clientProcessFadeTicks;
        bench.previousClientProximityTicks = bench.clientProximityTicks;

        int proximityDuration = Math.max(1, AscensionClientConfig.apexOrbActivationTicks());
        double proximityRadius = AscensionClientConfig.apexOrbProximityRadius();
        // Proximity is a presentation modifier of an already-valid Apex structure, never a second
        // activation source. An incomplete bench therefore cannot wake or retain the bench orb.
        bench.clientPlayerNearby = valid && level.getNearestPlayer(
            pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D, proximityRadius, false) != null;
        if (bench.clientPlayerNearby) {
            if (bench.clientProximityTicks < proximityDuration) bench.clientProximityTicks++;
        }
        else {
            // Settle faster than wake-up so invalidation/player departure collapses the sphere cleanly.
            bench.clientProximityTicks = Math.max(0, bench.clientProximityTicks - 2);
        }

        if (!bench.clientInitialized) {
            bench.clientInitialized = true;
            bench.clientStructureValid = valid;
            bench.clientSealTicks = valid ? duration : 0;
            bench.previousClientSealTicks = bench.clientSealTicks;
        }
        else if (valid) {
            bench.clientStructureValid = true;
            if (bench.clientSealTicks < duration) bench.clientSealTicks++;
        }
        else {
            bench.clientStructureValid = false;
            bench.clientSealTicks = Math.max(0, bench.clientSealTicks - 2);
        }

        if (bench.clientProcessing) {
            if (bench.clientProcessTicks < bench.clientProcessDuration) bench.clientProcessTicks++;
        }
        else if (bench.clientProcessFadeTicks > 0) {
            bench.clientProcessFadeTicks--;
        }
    }

    public float activationProgress(float partialTick) {
        int duration = Math.max(1, AscensionClientConfig.apexActivationTicks());
        if (AscensionClientConfig.reducedMotion()) return this.clientSealTicks > 0 || this.clientStructureValid ? 1.0F : 0.0F;
        float ticks = lerp(this.previousClientSealTicks, this.clientSealTicks, partialTick);
        float linear = clamp01(ticks / duration);
        float inv = 1.0F - linear;
        return 1.0F - inv * inv * inv;
    }


    /** Visual-only interpolation for the dormant->raised Apex bench seal sphere. */
    public float proximityActivation(float partialTick) {
        int duration = Math.max(1, AscensionClientConfig.apexOrbActivationTicks());
        if (AscensionClientConfig.reducedMotion()) return this.clientStructureValid && this.clientPlayerNearby ? 1.0F : 0.0F;
        float ticks = lerp(this.previousClientProximityTicks, this.clientProximityTicks, partialTick);
        float linear = clamp01(ticks / duration);
        // Smoothstep avoids a visible snap when the three rings begin tilting out of the bench plane.
        return linear * linear * (3.0F - 2.0F * linear);
    }

    public float processingGlow(float partialTick) {
        if (this.clientProcessing) {
            if (AscensionClientConfig.reducedMotion()) return 1.0F;
            float ticks = lerp(this.previousClientProcessTicks, this.clientProcessTicks, partialTick);
            return clamp01(ticks / Math.max(1, this.clientProcessDuration));
        }
        if (this.clientProcessFadeTicks > 0 || this.previousClientProcessFadeTicks > 0) {
            float ticks = lerp(this.previousClientProcessFadeTicks, this.clientProcessFadeTicks, partialTick);
            return clamp01(ticks / 6.0F);
        }
        return 0.0F;
    }

    private static float lerp(int from, int to, float partialTick) {
        return from + (to - from) * partialTick;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    public int processTicks() { return this.processTicks; }
    public int processDuration() { return AscensionServerConfig.apexProcessTicks(); }
    public int processFadeTicks() { return this.processFadeTicks; }
    public boolean processing() { return this.processTicks > 0; }

    @Override
    public boolean triggerEvent(int id, int type) {
        if (id == EVENT_ACTIVATE) {
            this.clientInitialized = true;
            this.clientStructureValid = true;
            this.clientSealTicks = 0;
            this.previousClientSealTicks = 0;
            return true;
        }
        if (id == EVENT_DEACTIVATE) {
            this.clientStructureValid = false;
            this.clientPlayerNearby = false;
            return true;
        }
        if (id == EVENT_PROCESS_START) {
            this.clientProcessing = true;
            this.clientProcessDuration = Math.max(1, type);
            this.clientProcessTicks = 0;
            this.previousClientProcessTicks = 0;
            this.clientProcessFadeTicks = 0;
            this.previousClientProcessFadeTicks = 0;
            return true;
        }
        if (id == EVENT_PROCESS_COMPLETE) {
            this.clientProcessing = false;
            this.clientProcessTicks = this.clientProcessDuration;
            this.previousClientProcessTicks = this.clientProcessDuration;
            this.clientProcessFadeTicks = Math.max(1, type);
            this.previousClientProcessFadeTicks = this.clientProcessFadeTicks;
            return true;
        }
        if (id == EVENT_PROCESS_CANCEL) {
            this.clientProcessing = false;
            this.clientProcessFadeTicks = Math.max(1, type);
            this.previousClientProcessFadeTicks = this.clientProcessFadeTicks;
            return true;
        }
        return super.triggerEvent(id, type);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
        if (this.reforgeSeed != 0) tag.putInt(ApexReforgeOffers.REFORGE_SEED_TAG, this.reforgeSeed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items.clear();
        ContainerHelper.loadAllItems(tag, this.items, registries);
        this.reforgeSeed = tag.contains(ApexReforgeOffers.REFORGE_SEED_TAG, Tag.TAG_INT)
            ? tag.getInt(ApexReforgeOffers.REFORGE_SEED_TAG)
            : 0;
        this.clearPendingProcess();
        this.processFadeTicks = 0;
    }

    @Override public int getContainerSize() { return SLOT_COUNT; }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getItem(int slot) { return this.items.get(slot); }

    /**
     * Automation follows the same slot-admission contract as the menu and cannot mutate a timed
     * transaction in flight. The result slot is machine-owned and therefore never accepts input.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return !this.processing()
            && slot >= 0 && slot < INPUT_SLOTS
            && WorkstationInputRules.accepts(WorkstationKind.APEX_ASCENSION_BENCH, slot, stack);
    }

    /**
     * Completed output remains automatable, while source slots are frozen during timed work so
     * external inventories cannot race the server-owned source snapshot.
     */
    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        if (slot == RESULT_SLOT) return true;
        return !this.processing() && slot >= 0 && slot < INPUT_SLOTS;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(this.items, slot, amount);
        if (!removed.isEmpty()) this.setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(this.items, slot);
        if (!removed.isEmpty()) this.setChanged();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        if (!stack.isEmpty() && stack.getCount() > this.getMaxStackSize(stack)) {
            stack.setCount(this.getMaxStackSize(stack));
        }
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level == null || this.level.getBlockEntity(this.worldPosition) != this) return false;
        return player.distanceToSqr(
            this.worldPosition.getX() + 0.5D,
            this.worldPosition.getY() + 0.5D,
            this.worldPosition.getZ() + 0.5D
        ) <= 64.0D;
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }
}
