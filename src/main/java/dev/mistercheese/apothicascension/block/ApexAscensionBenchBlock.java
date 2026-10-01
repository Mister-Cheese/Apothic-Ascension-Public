// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.block;

import dev.mistercheese.apothicascension.block.entity.ApexBenchBlockEntity;
import dev.mistercheese.apothicascension.menu.AscensionWorkstationMenu;
import dev.mistercheese.apothicascension.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Center block for the final-tier five-block Ascension multiblock and persistent Apex processor. */
public final class ApexAscensionBenchBlock extends Block implements EntityBlock {
    // Match the supplied Blockbench model used by the earlier Beta 9 live-test build.
    // The authored cap intentionally overhangs the block by one model unit on X/Z.
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D),
        Block.box(1.0D, 2.0D, 1.0D, 15.0D, 4.0D, 15.0D),
        Block.box(2.0D, 4.0D, 2.0D, 14.0D, 11.0D, 14.0D),
        Block.box(1.0D, 11.0D, 1.0D, 15.0D, 12.0D, 15.0D),
        Block.box(-1.0D, 12.0D, -1.0D, 17.0D, 15.0D, 17.0D)
    );

    public ApexAscensionBenchBlock(Properties properties) { super(properties.noOcclusion()); }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ApexBenchBlockEntity(pos, state); }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ApexBenchBlockEntity bench) bench.markFreshPlacement();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.APEX_ASCENSION_BENCH.get()) return null;
        if (level.isClientSide) {
            return (lvl, pos, blockState, be) -> ApexBenchBlockEntity.clientTick(lvl, pos, blockState, (ApexBenchBlockEntity) be);
        }
        return (lvl, pos, blockState, be) -> ApexBenchBlockEntity.serverTick(lvl, pos, blockState, (ApexBenchBlockEntity) be);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
            (containerId, inventory, player) -> {
                if (!(level.getBlockEntity(pos) instanceof ApexBenchBlockEntity bench)) return null;
                return AscensionWorkstationMenu.serverApex(
                    containerId, inventory, ContainerLevelAccess.create(level, pos), bench);
            },
            Component.translatable("container.apothic_ascension.apex_ascension_bench"));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!ApexMultiblock.isValid(level, pos)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.apothic_ascension.apex.incomplete"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) serverPlayer.openMenu(this.getMenuProvider(state, level, pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ApexBenchBlockEntity bench) {
            // Removing the center destroys the block entity before its next server tick, so preserve
            // the same deactivation audio contract used when a pylon invalidates the structure.
            if (bench.serverStructureActive()) {
                level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.72F, 1.0F);
            }
            Containers.dropContents(level, pos, bench);
            // The discarded block entity can remain referenced by an already-open menu until the
            // server closes it. Clear its backing storage after spawning drops so that even an
            // in-tick stale-menu packet cannot recover a second copy from the dead container.
            bench.clearContent();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
