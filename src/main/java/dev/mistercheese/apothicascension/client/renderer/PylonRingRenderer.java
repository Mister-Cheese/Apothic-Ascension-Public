// SPDX-License-Identifier: MPL-2.0

package dev.mistercheese.apothicascension.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.mistercheese.apothicascension.ApothicAscension;
import dev.mistercheese.apothicascension.block.entity.AscensionPylonBlockEntity;
import dev.mistercheese.apothicascension.config.AscensionClientConfig;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Two small fullbright gyroscopic rings, visible only while this pylon belongs to a valid Apex structure. */
public final class PylonRingRenderer implements BlockEntityRenderer<AscensionPylonBlockEntity> {
    private static final ResourceLocation RING = ResourceLocation.fromNamespaceAndPath(
        ApothicAscension.MODID, "textures/entity/pylon_ring.png");

    public PylonRingRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(AscensionPylonBlockEntity pylon, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light, int overlay) {
        if (!AscensionClientConfig.renderMagicCircles() || !AscensionClientConfig.renderPylonRings() || pylon.getLevel() == null) return;

        float activation = pylon.activationProgress(partialTick);
        if (activation <= 0.001F) return;

        float opacity = AscensionClientConfig.circleOpacity() * 0.84F * activation;
        float speed = AscensionClientConfig.reducedMotion() ? 0.0F : AscensionClientConfig.pylonRingRotationSpeed();
        float time = pylon.getLevel().getGameTime() + partialTick;
        float phase = pylon.rotationPhaseDegrees();
        float rate = pylon.rotationRateScale();
        float settleScale = 0.42F + 0.58F * activation;

        poseStack.pushPose();
        poseStack.translate(0.5D, 1.50D, 0.5D);
        poseStack.scale(settleScale, settleScale, settleScale);

        // Vertical hoop sweeping around the Y axis.
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees((time * speed * rate + phase) % 360.0F));
        drawXY(poseStack, buffer, 0.29F, opacity, 255, 235, 172);
        poseStack.popPose();

        // Horizontal hoop rolling around X with independent rate/phase to prevent mechanical lockstep.
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees((time * speed * 0.77F / rate + phase * 0.63F + 72.0F) % 360.0F));
        drawXZ(poseStack, buffer, 0.24F, opacity * 0.92F, 221, 184, 255);
        poseStack.popPose();

        poseStack.popPose();
    }

    private static void drawXY(PoseStack poseStack, MultiBufferSource buffer, float radius, float alpha, int r, int g, int b) {
        VertexConsumer out = buffer.getBuffer(RenderType.entityTranslucentEmissive(RING));
        PoseStack.Pose pose = poseStack.last();
        int a = alpha(alpha);
        quadXY(out, pose, radius, 0.0F, r, g, b, a, false);
        quadXY(out, pose, radius, 0.0F, r, g, b, a, true);
    }

    private static void drawXZ(PoseStack poseStack, MultiBufferSource buffer, float radius, float alpha, int r, int g, int b) {
        VertexConsumer out = buffer.getBuffer(RenderType.entityTranslucentEmissive(RING));
        PoseStack.Pose pose = poseStack.last();
        int a = alpha(alpha);
        quadXZ(out, pose, radius, 0.0F, r, g, b, a, false);
        quadXZ(out, pose, radius, 0.0F, r, g, b, a, true);
    }

    private static int alpha(float value) {
        return Math.max(1, Math.min(255, Math.round(value * 255.0F)));
    }

    private static void quadXY(VertexConsumer out, PoseStack.Pose pose, float r0, float z, int r, int g, int b, int a, boolean reverse) {
        if (!reverse) {
            vertex(out, pose, -r0, -r0, z, 0, 1, r, g, b, a, 0, 0, 1);
            vertex(out, pose,  r0, -r0, z, 1, 1, r, g, b, a, 0, 0, 1);
            vertex(out, pose,  r0,  r0, z, 1, 0, r, g, b, a, 0, 0, 1);
            vertex(out, pose, -r0,  r0, z, 0, 0, r, g, b, a, 0, 0, 1);
        }
        else {
            vertex(out, pose, -r0,  r0, z, 0, 0, r, g, b, a, 0, 0, -1);
            vertex(out, pose,  r0,  r0, z, 1, 0, r, g, b, a, 0, 0, -1);
            vertex(out, pose,  r0, -r0, z, 1, 1, r, g, b, a, 0, 0, -1);
            vertex(out, pose, -r0, -r0, z, 0, 1, r, g, b, a, 0, 0, -1);
        }
    }

    private static void quadXZ(VertexConsumer out, PoseStack.Pose pose, float r0, float y, int r, int g, int b, int a, boolean reverse) {
        if (!reverse) {
            vertex(out, pose, -r0, y, -r0, 0, 1, r, g, b, a, 0, 1, 0);
            vertex(out, pose, -r0, y,  r0, 0, 0, r, g, b, a, 0, 1, 0);
            vertex(out, pose,  r0, y,  r0, 1, 0, r, g, b, a, 0, 1, 0);
            vertex(out, pose,  r0, y, -r0, 1, 1, r, g, b, a, 0, 1, 0);
        }
        else {
            vertex(out, pose,  r0, y, -r0, 1, 1, r, g, b, a, 0, -1, 0);
            vertex(out, pose,  r0, y,  r0, 1, 0, r, g, b, a, 0, -1, 0);
            vertex(out, pose, -r0, y,  r0, 0, 0, r, g, b, a, 0, -1, 0);
            vertex(out, pose, -r0, y, -r0, 0, 1, r, g, b, a, 0, -1, 0);
        }
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               int r, int g, int b, int a, float nx, float ny, float nz) {
        out.addVertex(pose, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(0)
            .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }

    @Override public boolean shouldRenderOffScreen(AscensionPylonBlockEntity pylon) { return false; }
    @Override public int getViewDistance() { return 48; }
}
