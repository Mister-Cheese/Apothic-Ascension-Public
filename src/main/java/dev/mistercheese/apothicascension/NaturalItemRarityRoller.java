// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension;

import dev.mistercheese.apothicascension.config.AscensionServerConfig;
import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.loot.LootController;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.mobs.types.Invader;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Natural post-Mythic item distribution with a bounded-Luck, super-exponential upper tail. */
public final class NaturalItemRarityRoller {
    public enum Source { STANDARD_CHEST, VALUABLE_CHEST, TREASURE_ENTITY, BOSS_OR_GATEWAY, OTHER }

    private static final Set<ResourceLocation> VALUABLE_CONTAINER_TABLES = Set.of(
        ResourceLocation.withDefaultNamespace("chests/ancient_city"),
        ResourceLocation.withDefaultNamespace("chests/bastion_treasure"),
        ResourceLocation.withDefaultNamespace("chests/buried_treasure"),
        ResourceLocation.withDefaultNamespace("chests/end_city_treasure"),
        ResourceLocation.withDefaultNamespace("chests/woodland_mansion")
    );

    private NaturalItemRarityRoller() {}

    public static LootRarity maybeAscend(LootRarity base, LootContext loot, GenContext context) {
        if (!AscensionServerConfig.naturalItemAscension()) return base;
        if (!RarityResolver.isApotheosisMythic(base)) return base;
        if (context == null || context.tier() != WorldTier.PINNACLE) return base;
        return roll(base, context, AscensionStageTracker.resolveForLoot(loot, context), classify(loot));
    }

    public static ItemStack maybeAscendGenerated(ItemStack generated, LootContext loot, GenContext context) {
        if (generated == null || generated.isEmpty() || context == null) return generated;
        var holder = AffixHelper.getRarity(generated);
        if (!holder.isBound()) return generated;
        LootRarity base = holder.get();
        LootRarity upgraded = maybeAscend(base, loot, context);
        return upgraded == base ? generated : LootController.createLootItem(generated, upgraded, context);
    }

    public static LootRarity maybeAscendBoss(LootRarity base, GenContext context, AscensionStage stage) {
        if (!AscensionServerConfig.naturalItemAscension()) return base;
        if (!RarityResolver.isApotheosisMythic(base)) return base;
        if (context == null || context.tier() != WorldTier.PINNACLE) return base;
        return roll(base, context, stage == null ? AscensionStage.LOCKED : stage, Source.BOSS_OR_GATEWAY);
    }

    private static LootRarity roll(LootRarity base, GenContext context, AscensionStage stage, Source source) {
        int frontierIndex = postMythicIndexForSort(stage.naturalSortCap());
        RandomSource random = context.rand();
        double luckFactor = boundedLuckFactor(context.luck());
        for (int index = AscensionRarity.APOTHEOTIC.postMythicIndex(); index >= 1; index--) {
            double exponent = naturalLog2Denominator(source, index) * AscensionServerConfig.naturalItemCurveScale();
            exponent += frontierPenaltyLog2(Math.max(0, index - frontierIndex)) * AscensionServerConfig.naturalFrontierPenaltyScale();
            if (NaturalAscensionOdds.passes(random, exponent, luckFactor, AscensionServerConfig.naturalProbabilityCeiling())) {
                LootRarity upgraded = resolveByPostMythicIndex(index);
                if (upgraded != null) return upgraded;
            }
        }
        return base;
    }

    /** Source-specific log2 denominator. Quadratic exponent means the actual odds fall super-exponentially by tier. */
    static double naturalLog2Denominator(Source source, int index) {
        if (index <= 0) return Double.POSITIVE_INFINITY;
        double i = index;
        return switch (source) {
            case STANDARD_CHEST -> 0.50D * i * i + 3.50D * i;
            case VALUABLE_CHEST -> 0.50D * i * i + 2.50D * i;
            case TREASURE_ENTITY -> 0.50D * i * i + 2.00D * i;
            case BOSS_OR_GATEWAY -> 0.35D * i * i + 1.25D * i;
            case OTHER -> 0.55D * i * i + 3.75D * i;
        };
    }

    /** Additional soft-frontier exponent: 2d^2 + 6d. Even LOCKED has a nonzero but heavily suppressed tail. */
    static double frontierPenaltyLog2(int tiersBeyondFrontier) {
        if (tiersBeyondFrontier <= 0) return 0.0D;
        double d = tiersBeyondFrontier;
        return 2.0D * d * d + 6.0D * d;
    }

    public static double boundedLuckFactor(float luck) {
        return NaturalAscensionOdds.boundedLuckFactor(luck, AscensionServerConfig.naturalItemLuckBonus(), 12.0D);
    }

    public static Source classify(LootContext loot) {
        if (loot == null) return Source.OTHER;
        Entity entity = loot.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (entity != null) {
            if (entity instanceof LivingEntity living && living.getPersistentData().getBoolean(Invader.BOSS_KEY)) return Source.BOSS_OR_GATEWAY;
            EntityType<?> type = entity.getType();
            if (type == EntityType.WITHER || type == EntityType.ENDER_DRAGON) return Source.BOSS_OR_GATEWAY;
            if (type == EntityType.WARDEN || type == EntityType.ELDER_GUARDIAN || type == EntityType.RAVAGER) return Source.TREASURE_ENTITY;
        }
        ResourceLocation table = loot.getQueriedLootTableId();
        if (table != null && VALUABLE_CONTAINER_TABLES.contains(table)) return Source.VALUABLE_CHEST;
        if (loot.getParamOrNull(LootContextParams.BLOCK_ENTITY) instanceof Container) return Source.STANDARD_CHEST;
        return Source.OTHER;
    }

    private static int postMythicIndexForSort(int sort) { return sort <= 700 ? 0 : Math.max(0, (sort - 700) / 100); }

    private static LootRarity resolveByPostMythicIndex(int index) {
        AscensionRarity target = AscensionRarity.byRank(index + 5);
        return RarityResolver.ascensionLootRarity(target);
    }
}
