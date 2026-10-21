package com.kintil555.football.client;

import com.kintil555.football.FootballMod;
import com.kintil555.football.action.BallPhysics;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Draws the dashed ball path and a ring where it lands. No bounce preview. Emissive, so lighting never darkens it. */
public final class FootballPreviewRenderer {
    private static final Identifier WHITE_TEXTURE = Identifier.fromNamespaceAndPath(FootballMod.MOD_ID, "textures/misc/white.png");
    private static final float DASH_HALF_WIDTH = 0.035F;
    private static final float RING_OUTER = 0.55F;
    private static final float RING_INNER = 0.45F;
    private static final int RING_SEGMENTS = 32;
    private static final int COLOR = ARGB.color(215, 255, 244, 196);

    private FootballPreviewRenderer() {
    }

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(FootballPreviewRenderer::collect);
    }

    private static void collect(LevelRenderContext context) {
        BallPhysics.Trajectory trajectory = FootballPreview.get();
        if (trajectory == null || trajectory.points().size() < 2) {
            return;
        }
        CameraRenderState camera = context.levelState().cameraRenderState;
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        context.submitNodeCollector().submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucentEmissive(WHITE_TEXTURE, false),
                (pose, buffer) -> build(pose, buffer, trajectory, camera.pos));
        poseStack.popPose();
    }

    private static void build(PoseStack.Pose pose, VertexConsumer buffer, BallPhysics.Trajectory trajectory, Vec3 cam) {
        List<Vec3> points = trajectory.points();
        // Dashes: draw every other tick segment as a camera-facing ribbon.
        for (int i = 0; i + 1 < points.size(); i += 2) {
            Vec3 a = points.get(i).subtract(cam);
            Vec3 b = points.get(i + 1).subtract(cam);
            Vec3 dir = b.subtract(a);
            Vec3 side = dir.cross(a.add(b).scale(0.5));
            if (side.lengthSqr() < 1.0E-9) {
                continue;
            }
            side = side.normalize().scale(DASH_HALF_WIDTH);
            vertex(pose, buffer, a.subtract(side));
            vertex(pose, buffer, a.add(side));
            vertex(pose, buffer, b.add(side));
            vertex(pose, buffer, b.subtract(side));
        }
        Vec3 landing = trajectory.landing();
        if (landing == null) {
            return;
        }
        Vec3 c = landing.subtract(cam).add(0.0, 0.03, 0.0);
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / RING_SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / RING_SEGMENTS;
            vertex(pose, buffer, c.add(Mth.cos(a0) * RING_INNER, 0.0, Mth.sin(a0) * RING_INNER));
            vertex(pose, buffer, c.add(Mth.cos(a0) * RING_OUTER, 0.0, Mth.sin(a0) * RING_OUTER));
            vertex(pose, buffer, c.add(Mth.cos(a1) * RING_OUTER, 0.0, Mth.sin(a1) * RING_OUTER));
            vertex(pose, buffer, c.add(Mth.cos(a1) * RING_INNER, 0.0, Mth.sin(a1) * RING_INNER));
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 p) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(COLOR)
                .setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
