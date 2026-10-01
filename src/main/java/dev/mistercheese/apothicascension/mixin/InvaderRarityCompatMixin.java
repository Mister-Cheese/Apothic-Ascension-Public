// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.mixin;

import dev.mistercheese.apothicascension.AscensionRarity;
import dev.mistercheese.apothicascension.AscensionStage;
import dev.mistercheese.apothicascension.AscensionStageTracker;
import dev.mistercheese.apothicascension.NaturalItemRarityRoller;
import dev.mistercheese.apothicascension.RarityResolver;
import dev.mistercheese.apothicascension.registry.ModAttachments;
import dev.shadowsoffire.apotheosis.Apotheosis;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.mobs.types.Invader;
import dev.shadowsoffire.apotheosis.mobs.util.BossStats;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Extends Invader rarity selection with explicit spawn-position/player context and supplies a
 * strongest-native BossStats fallback for Ascension rarities.
 */
@Mixin(value = Invader.class, remap = false)
public abstract class InvaderRarityCompatMixin {
    @Redirect(
        method = "createBoss(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Ldev/shadowsoffire/apotheosis/tiers/GenContext;Ldev/shadowsoffire/apotheosis/loot/LootRarity;)Lnet/minecraft/world/entity/Mob;",
        at = @At(value = "INVOKE", target = "Ldev/shadowsoffire/apotheosis/mobs/types/Invader;initBoss(Lnet/minecraft/world/entity/Mob;Ldev/shadowsoffire/apotheosis/tiers/GenContext;Ldev/shadowsoffire/apotheosis/loot/LootRarity;)V"),
        require = 1,
        allow = 1,
        remap = false)
    private void apothicAscension$initBossWithStage(
        Invader self,
        Mob mob,
        GenContext context,
        LootRarity requested,
        ServerLevelAccessor level,
        BlockPos pos,
        GenContext methodContext,
        LootRarity methodRequested
    ) {
        LootRarity effective = requested;
        if (effective == null) {
            LootRarity base = LootRarity.random(context, self.stats().keySet());
            Player nearest = level.getLevel().getNearestPlayer(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 128.0D, false);
            AscensionStage stage = nearest == null
                ? AscensionStage.LOCKED
                : AscensionStageTracker.getStored(nearest);
            effective = NaturalItemRarityRoller.maybeAscendBoss(base, context, stage);
        }
        self.initBoss(mob, context, effective);
        AscensionRarity ascension = RarityResolver.ascensionRarity(effective);
        mob.setData(ModAttachments.BOSS_RARITY_CACHE, ascension == null ? -1 : ascension.rank());
    }

    @Redirect(
        method = "initBoss",
        at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0),
        require = 1,
        allow = 1,
        remap = false)
    private Object apothicAscension$bossStatsFallback(Map<LootRarity, BossStats> stats, Object requested) {
        BossStats exact = stats.get(requested);
        if (exact != null || !(requested instanceof LootRarity rarity)
            || RarityResolver.ascensionRarity(rarity) == null) {
            return exact;
        }

        BossStats fallback = null;
        int bestSort = Integer.MIN_VALUE;
        for (Map.Entry<LootRarity, BossStats> entry : stats.entrySet()) {
            LootRarity candidate = entry.getKey();
            BossStats candidateStats = entry.getValue();
            if (candidate == null || candidateStats == null) continue;

            ResourceLocation candidateId = RarityRegistry.INSTANCE.getKey(candidate);
            if (candidateId == null || !Apotheosis.MODID.equals(candidateId.getNamespace())) continue;

            if (candidate.sortIndex() > bestSort) {
                bestSort = candidate.sortIndex();
                fallback = candidateStats;
            }
        }
        return fallback;
    }
}
