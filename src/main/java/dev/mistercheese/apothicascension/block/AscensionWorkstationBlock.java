// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.block;

import dev.mistercheese.apothicascension.menu.AscensionWorkstationMenu;
import dev.mistercheese.apothicascension.menu.WorkstationKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Stateless interaction block for an Ascension workstation. */
public final class AscensionWorkstationBlock extends Block {
    private final WorkstationKind kind;

    public AscensionWorkstationBlock(Properties properties, WorkstationKind kind) {
        super(properties);
        this.kind = kind;
    }

    public WorkstationKind kind() {
        return this.kind;
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider(
            (containerId, inventory, player) -> AscensionWorkstationMenu.server(
                this.kind,
                containerId,
                inventory,
                ContainerLevelAccess.create(level, pos)
            ),
            Component.translatable("container.apothic_ascension." + this.kind.key())
        );
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult
    ) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this.getMenuProvider(state, level, pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
