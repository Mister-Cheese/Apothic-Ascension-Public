// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.block;

import dev.mistercheese.apothicascension.block.entity.AscensionPylonBlockEntity;
import dev.mistercheese.apothicascension.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Authored Apex pylon geometry plus a lightweight render anchor for its emissive gyroscopic rings. */
public final class AscensionPylonBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(5.0D, 0.0D, 5.0D, 11.0D, 2.0D, 11.0D),
        Block.box(6.0D, 2.0D, 6.0D, 10.0D, 12.0D, 10.0D),
        Block.box(6.5D, 12.0D, 6.5D, 9.5D, 16.0D, 9.5D),
        Block.box(7.25D, 16.0D, 7.25D, 8.75D, 19.0D, 8.75D)
    );

    public AscensionPylonBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AscensionPylonBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.ASCENSION_PYLON.get() || !level.isClientSide) return null;
        return (lvl, pos, blockState, be) -> AscensionPylonBlockEntity.clientTick(lvl, pos, blockState, (AscensionPylonBlockEntity) be);
    }
}
