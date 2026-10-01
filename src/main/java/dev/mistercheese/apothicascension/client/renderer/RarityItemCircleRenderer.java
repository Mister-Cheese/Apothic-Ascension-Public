// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.AscensionRarity;
import dev.mistercheese.apothicascension.GemTier;
import dev.mistercheese.apothicascension.RarityResolver;
import dev.mistercheese.apothicascension.config.AscensionClientConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Bounded nearby-item cache and shader-friendly in-world rarity-circle renderer. */
public final class RarityItemCircleRenderer {
    private static final ResourceLocation[] CIRCLES = {
        null,
        circle("minor_1.png"), circle("minor_1.png"), circle("minor_1.png"),
        circle("minor_2.png"), circle("minor_2.png"), circle("minor_2.png"),
        circle("minor_3.png"), circle("minor_3.png"), circle("minor_3.png"),
        circle("minor_4.png"), circle("minor_4.png"),
        circle("minor_5.png"), circle("minor_5.png")
    };

    private static final List<ItemEntity> VISIBLE = new ArrayList<>();
    private static final int MAX_GROUND_SCAN_BLOCKS = 32;
    private static final double GROUND_EPSILON = 0.004D;
    private static final double COLLISION_EPSILON = 1.0E-6D;
    private static int ticksUntilScan;

    private RarityItemCircleRenderer() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !enabled()) {
            VISIBLE.clear();
            ticksUntilScan = 0;
            return;
        }
        if (ticksUntilScan-- > 0) return;
        ticksUntilScan = AscensionClientConfig.rarityScanIntervalTicks() - 1;
        int distance = AscensionClientConfig.circleRenderDistance();
        VISIBLE.clear();
        VISIBLE.addAll(mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(distance),
            entity -> entity.isAlive() && rarity(entity) != null));
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !enabled() || VISIBLE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || event.getPoseStack() == null) return;
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        double maxDistanceSq = (double) AscensionClientConfig.circleRenderDistance() * AscensionClientConfig.circleRenderDistance();

        for (ItemEntity entity : List.copyOf(VISIBLE)) {
            if (!entity.isAlive() || entity.level() != mc.level || entity.distanceToSqr(mc.player) > maxDistanceSq) continue;
            AscensionRarity rarity = rarity(entity);
            if (rarity == null) continue;
            int tier = rarity.postMythicIndex();
            ResourceLocation texture = CIRCLES[Math.max(1, Math.min(13, tier))];
            double worldX = Mth.lerp(partial, entity.xo, entity.getX());
            double worldItemY = Mth.lerp(partial, entity.yo, entity.getY());
            double worldZ = Mth.lerp(partial, entity.zo, entity.getZ());
            double worldGroundY = supportingSurfaceY(entity, worldX, worldItemY, worldZ);
            if (!Double.isFinite(worldGroundY)) continue;

            float x = (float) (worldX - camera.x);
            float y = (float) (worldGroundY - camera.y);
            float z = (float) (worldZ - camera.z);
            float radius = 0.42F + 0.024F * tier;
            float speed = AscensionClientConfig.reducedMotion() ? 0.0F : AscensionClientConfig.rarityRotationSpeed();
            float angle = ((mc.level.getGameTime() + partial) * speed + entity.getId() * 13.0F) % 360.0F;
            int color = GemTier.byId(tier).color();
            float opacity = AscensionClientConfig.circleOpacity() * (0.48F + 0.026F * tier);

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));
            draw(poseStack, buffers, texture, radius, color, opacity, 0.0F);
            float glow = AscensionClientConfig.circleGlowStrength();
            if (glow > 0.05F) draw(poseStack, buffers, texture, radius * (1.02F + 0.012F * glow), color,
                opacity * Math.min(0.42F, 0.12F + 0.12F * glow), 0.001F);
            poseStack.popPose();
        }
        buffers.endBatch();
    }

    /**
     * Finds the top collision surface directly below the item center. The returned Y is a
     * world-space ground coordinate with only a tiny anti-z-fighting lift; it deliberately
     * does not derive from the dropped item's bob/vertical position.
     */
    private static double supportingSurfaceY(ItemEntity entity, double worldX, double itemY, double worldZ) {
        var level = entity.level();
        int blockX = Mth.floor(worldX);
        int blockZ = Mth.floor(worldZ);
        int startY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(itemY + 0.5D));
        int minY = Math.max(level.getMinBuildHeight(), startY - MAX_GROUND_SCAN_BLOCKS);
        double localX = worldX - blockX;
        double localZ = worldZ - blockZ;

        for (int blockY = startY; blockY >= minY; blockY--) {
            BlockPos pos = new BlockPos(blockX, blockY, blockZ);
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;

            double top = Double.NEGATIVE_INFINITY;
            for (AABB box : shape.toAabbs()) {
                if (localX + COLLISION_EPSILON < box.minX || localX - COLLISION_EPSILON > box.maxX
                    || localZ + COLLISION_EPSILON < box.minZ || localZ - COLLISION_EPSILON > box.maxZ) {
                    continue;
                }
                top = Math.max(top, box.maxY);
            }
            if (!Double.isFinite(top)) continue;

            double surfaceY = blockY + top;
            // Ignore collision geometry above the item (for example, the underside of an overhang).
            if (surfaceY <= itemY + 0.5D) return surfaceY + GROUND_EPSILON;
        }

        // Never let a ground circle turn into a hovering item-attached marker.
        return Double.NaN;
    }

    private static boolean enabled() {
        return AscensionClientConfig.renderMagicCircles() && AscensionClientConfig.renderRarityItemCircles();
    }

    private static AscensionRarity rarity(ItemEntity entity) {
        return RarityResolver.ascensionRarity(entity.getItem());
    }

    private static void draw(PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture, float radius, int rgb, float alpha, float y) {
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityTranslucentEmissive(texture));
        PoseStack.Pose pose = poseStack.last();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = Math.max(1, Math.min(255, Math.round(Math.min(1.0F, alpha) * 255.0F)));
        vertex(vertices, pose, -radius, -radius, y, 0, 0, r, g, b, a);
        vertex(vertices, pose, -radius, radius, y, 0, 1, r, g, b, a);
        vertex(vertices, pose, radius, radius, y, 1, 1, r, g, b, a);
        vertex(vertices, pose, radius, -radius, y, 1, 0, r, g, b, a);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float z, float y, float u, float v, int r, int g, int b, int a) {
        out.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(0)
            .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static ResourceLocation circle(String name) {
        return ResourceLocation.fromNamespaceAndPath(ApothicAscension.MODID, "textures/entity/circles/" + name);
    }
}
