// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.block.entity;

import dev.mistercheese.apothicascension.block.ApexMultiblock;
import dev.mistercheese.apothicascension.config.AscensionClientConfig;
import dev.mistercheese.apothicascension.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Client presentation anchor for a pylon linked to a valid Apex structure. No gameplay state is stored here. */
public final class AscensionPylonBlockEntity extends BlockEntity {
    private boolean clientStructureValid;
    private int clientActivationTicks;
    private int previousClientActivationTicks;

    public AscensionPylonBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASCENSION_PYLON.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, AscensionPylonBlockEntity pylon) {
        boolean valid = ApexMultiblock.isPylonInValidStructure(level, pos);
        int duration = Math.max(1, AscensionClientConfig.apexActivationTicks());

        pylon.previousClientActivationTicks = pylon.clientActivationTicks;
        pylon.clientStructureValid = valid;
        if (valid) {
            if (pylon.clientActivationTicks < duration) pylon.clientActivationTicks++;
        }
        else {
            // Match the bench/floor seal teardown: disappear quickly, but do not pop in/out for one-frame updates.
            pylon.clientActivationTicks = Math.max(0, pylon.clientActivationTicks - 2);
        }
    }

    public float activationProgress(float partialTick) {
        int duration = Math.max(1, AscensionClientConfig.apexActivationTicks());
        if (AscensionClientConfig.reducedMotion()) return this.clientStructureValid ? 1.0F : 0.0F;
        float ticks = this.previousClientActivationTicks
            + (this.clientActivationTicks - this.previousClientActivationTicks) * partialTick;
        float linear = Math.max(0.0F, Math.min(1.0F, ticks / duration));
        return linear * linear * (3.0F - 2.0F * linear);
    }

    /** Stable per-position phase keeps all four pylons from rotating in mechanical lockstep. */
    public float rotationPhaseDegrees() {
        long seed = this.worldPosition.getX() * 31L + this.worldPosition.getY() * 13L + this.worldPosition.getZ() * 17L;
        return Math.floorMod(seed, 360L);
    }

    /** Tiny deterministic rate variation reads as four linked emitters rather than duplicated sprites. */
    public float rotationRateScale() {
        long seed = this.worldPosition.getX() * 2L + this.worldPosition.getY() * 3L + this.worldPosition.getZ() * 5L;
        return 0.95F + Math.floorMod(seed, 11L) * 0.01F;
    }
}
