// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.block.entity.ApexBenchBlockEntity;
import dev.mistercheese.apothicascension.config.AscensionClientConfig;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Fullbright Apex presentation. Every world-space effect is subordinate to the actual multiblock
 * activation envelope: invalid/incomplete benches never acquire a floor seal or bench orb merely
 * because their block entity exists or a player walks nearby.
 */
public final class ApexSealRenderer implements BlockEntityRenderer<ApexBenchBlockEntity> {
    private static final ResourceLocation SEAL = ResourceLocation.fromNamespaceAndPath(
        ApothicAscension.MODID, "textures/entity/apex_seal.png");

    public ApexSealRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ApexBenchBlockEntity bench, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light, int overlay) {
        if (!AscensionClientConfig.renderMagicCircles() || bench.getLevel() == null) return;

        float structureProgress = bench.activationProgress(partialTick);
        if (structureProgress <= 0.001F) return;

        float processGlow = bench.processingGlow(partialTick);
        if (AscensionClientConfig.renderApexSeal()) {
            renderFloorSeal(bench, partialTick, poseStack, buffer, structureProgress, processGlow);
        }
        if (AscensionClientConfig.renderApexOrb()) {
            renderBenchSphere(bench, partialTick, poseStack, buffer, structureProgress, processGlow);
        }
    }

    private static void renderFloorSeal(ApexBenchBlockEntity bench, float partialTick, PoseStack poseStack,
                                        MultiBufferSource buffer, float progress, float processGlow) {
        float rotationSpeed = AscensionClientConfig.reducedMotion() ? 0.0F : AscensionClientConfig.apexRotationSpeed();
        float angle = ((bench.getLevel().getGameTime() + partialTick) * rotationSpeed) % 360.0F;
        float opacity = AscensionClientConfig.circleOpacity()
            * progress
            * (0.62F + 0.38F * progress)
            * (1.0F + 0.22F * processGlow);
        float glow = AscensionClientConfig.circleGlowStrength() * (1.0F + 0.55F * processGlow);

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.015D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        float scale = (0.04F + 0.96F * progress) * (1.0F + 0.025F * processGlow);
        poseStack.scale(scale, 1.0F, scale);
        drawSealQuad(poseStack, buffer, 3.45F, opacity, 255, 241, 187);
        if (glow > 0.05F) {
            poseStack.pushPose();
            float haloScale = 1.006F + 0.010F * Math.min(2.5F, glow);
            poseStack.scale(haloScale, 1.0F, haloScale);
            drawSealQuad(poseStack, buffer, 3.45F,
                opacity * Math.min(0.55F, 0.16F + 0.18F * glow), 255, 231, 154);
            poseStack.popPose();
        }
        if (glow > 1.35F) {
            poseStack.pushPose();
            poseStack.scale(1.025F, 1.0F, 1.025F);
            drawSealQuad(poseStack, buffer, 3.45F, opacity * 0.16F, 255, 242, 194);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /**
     * Three nested seal planes. At wake=0 they are co-planar, still, and resting immediately above
     * the authored bench work surface. Player proximity (while the structure remains valid) raises
     * and unfolds the tiers into a slow three-axis gyroscope. This is presentation only.
     */
    private static void renderBenchSphere(ApexBenchBlockEntity bench, float partialTick, PoseStack poseStack,
                                          MultiBufferSource buffer, float structureProgress, float processGlow) {
        float wake = bench.proximityActivation(partialTick);
        float reducedMotionFactor = AscensionClientConfig.reducedMotion() ? 0.0F : 1.0F;
        float time = bench.getLevel().getGameTime() + partialTick;
        float speed = AscensionClientConfig.apexOrbRotationSpeed() * reducedMotionFactor;

        // Authored bench top ends at y=15/16. Start just above it; raise as the gyro unfolds.
        double y = 0.965D + 0.52D * wake;
        float processPulse = processGlow <= 0.001F
            ? 0.0F
            : (0.5F + 0.5F * (float) Math.sin(time * 0.42D)) * processGlow;
        float baseOpacity = AscensionClientConfig.circleOpacity()
            * structureProgress
            * (0.22F + 0.73F * wake)
            * (1.0F + 0.18F * processGlow);
        float scalePulse = (0.32F + 0.68F * structureProgress) * (1.0F + 0.030F * processPulse);

        poseStack.pushPose();
        poseStack.translate(0.5D, y, 0.5D);
        poseStack.scale(scalePulse, scalePulse, scalePulse);

        renderGyroTier(poseStack, buffer, time, speed, wake,
            0.58F, baseOpacity, 255, 226, 132, processGlow,
            38.0F, 24.0F, 1.00F, 0.54F, 18.0F);

        renderGyroTier(poseStack, buffer, time, speed, wake,
            0.43F, baseOpacity * 0.92F, 223, 193, 255, processGlow,
            -54.0F, 18.0F, -0.73F, 0.89F, 137.0F);

        renderGyroTier(poseStack, buffer, time, speed, wake,
            0.29F, baseOpacity * 0.88F, 255, 246, 205, processGlow,
            31.0F, -49.0F, 0.47F, -1.17F, 251.0F);

        poseStack.popPose();
    }

    private static void renderGyroTier(PoseStack poseStack, MultiBufferSource buffer,
                                       float time, float speed, float wake, float radius, float alpha,
                                       int r, int g, int b, float processGlow,
                                       float tiltX, float tiltZ, float precessionRate,
                                       float localSpinRate, float phase) {
        // Multiplying every angular component by wake guarantees a genuinely flat/still dormant state.
        float precession = ((time * speed * precessionRate + phase) * wake) % 360.0F;
        float localSpin = ((time * speed * localSpinRate + phase * 0.37F) * wake) % 360.0F;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(precession));
        poseStack.mulPose(Axis.XP.rotationDegrees(tiltX * wake));
        poseStack.mulPose(Axis.ZP.rotationDegrees(tiltZ * wake));
        poseStack.mulPose(Axis.YP.rotationDegrees(localSpin));
        drawTier(poseStack, buffer, radius, alpha, r, g, b, processGlow);
        poseStack.popPose();
    }

    private static void drawTier(PoseStack poseStack, MultiBufferSource buffer, float radius, float alpha,
                                 int r, int g, int b, float processGlow) {
        drawSealQuad(poseStack, buffer, radius, alpha, r, g, b);

        float glow = AscensionClientConfig.circleGlowStrength() * (1.0F + 0.45F * processGlow);
        if (glow > 0.05F) {
            poseStack.pushPose();
            float haloScale = 1.018F + 0.010F * Math.min(2.5F, glow);
            poseStack.scale(haloScale, haloScale, haloScale);
            drawSealQuad(poseStack, buffer, radius,
                alpha * Math.min(0.38F, 0.11F + 0.12F * glow), r, g, b);
            poseStack.popPose();
        }
    }

    /** Draws the seal texture on both sides of the local XZ plane. */
    private static void drawSealQuad(PoseStack poseStack, MultiBufferSource buffer, float radius, float alpha,
                                     int r, int g, int b) {
        if (alpha <= 0.001F) return;
        VertexConsumer out = buffer.getBuffer(RenderType.entityTranslucentEmissive(SEAL));
        PoseStack.Pose pose = poseStack.last();
        int a = Math.max(1, Math.min(255, Math.round(alpha * 255.0F)));

        vertex(out, pose, -radius, 0.0F, -radius, 0.0F, 0.0F, r, g, b, a, 0.0F, 1.0F, 0.0F);
        vertex(out, pose, -radius, 0.0F,  radius, 0.0F, 1.0F, r, g, b, a, 0.0F, 1.0F, 0.0F);
        vertex(out, pose,  radius, 0.0F,  radius, 1.0F, 1.0F, r, g, b, a, 0.0F, 1.0F, 0.0F);
        vertex(out, pose,  radius, 0.0F, -radius, 1.0F, 0.0F, r, g, b, a, 0.0F, 1.0F, 0.0F);

        vertex(out, pose,  radius, 0.0F, -radius, 1.0F, 0.0F, r, g, b, a, 0.0F, -1.0F, 0.0F);
        vertex(out, pose,  radius, 0.0F,  radius, 1.0F, 1.0F, r, g, b, a, 0.0F, -1.0F, 0.0F);
        vertex(out, pose, -radius, 0.0F,  radius, 0.0F, 1.0F, r, g, b, a, 0.0F, -1.0F, 0.0F);
        vertex(out, pose, -radius, 0.0F, -radius, 0.0F, 0.0F, r, g, b, a, 0.0F, -1.0F, 0.0F);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v,
                               int r, int g, int b, int a, float nx, float ny, float nz) {
        out.addVertex(pose, x, y, z)
            .setColor(r, g, b, a)
            .setUv(u, v)
            .setOverlay(0)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, nx, ny, nz);
    }

    @Override public boolean shouldRenderOffScreen(ApexBenchBlockEntity bench) { return true; }
    @Override public int getViewDistance() { return 64; }
}
