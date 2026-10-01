// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.block;

import dev.mistercheese.apothicascension.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Structural contract for the final-tier Apex Ascension Bench. */
public final class ApexMultiblock {
    public static final int PYLON_DISTANCE = 3;
    private static final Direction[] CARDINAL_DIRECTIONS = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    public static boolean isValid(Level level, BlockPos center) {
        if (level == null || center == null || !level.getBlockState(center).is(ModBlocks.APEX_ASCENSION_BENCH.get())) return false;
        for (BlockPos pylon : pylons(center)) {
            if (!level.getBlockState(pylon).is(ModBlocks.ASCENSION_PYLON.get())) return false;
        }
        return true;
    }

    /**
     * Returns true only when {@code pylonPos} is one of the four cardinal pylons belonging to a
     * currently valid Apex structure. This is the single presentation gate used by pylon renderers;
     * a standalone pylon must never acquire Apex rings merely because its block entity is ticking.
     */
    public static boolean isPylonInValidStructure(Level level, BlockPos pylonPos) {
        if (level == null || pylonPos == null || !level.getBlockState(pylonPos).is(ModBlocks.ASCENSION_PYLON.get())) return false;
        for (Direction direction : CARDINAL_DIRECTIONS) {
            BlockPos candidateCenter = pylonPos.relative(direction, PYLON_DISTANCE);
            if (isValid(level, candidateCenter)) return true;
        }
        return false;
    }

    public static List<BlockPos> pylons(BlockPos center) {
        return List.of(
            center.relative(Direction.NORTH, PYLON_DISTANCE),
            center.relative(Direction.EAST, PYLON_DISTANCE),
            center.relative(Direction.SOUTH, PYLON_DISTANCE),
            center.relative(Direction.WEST, PYLON_DISTANCE)
        );
    }

    private ApexMultiblock() {}
}
