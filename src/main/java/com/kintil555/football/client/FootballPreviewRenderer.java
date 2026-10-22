package com.kintil555.football.client;

import com.kintil555.football.FootballMod;
import com.kintil555.football.action.BallPhysics;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
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
    /** Dash ribbon half width at close range; it grows with the camera distance so far dashes stay readable. */
    private static final float DASH_HALF_WIDTH = 0.055F;
    private static final float DASH_MAX_SCALE = 3.5F;
    private static final float DASH_SCALE_DISTANCE = 6.0F;
    private static final float OUTLINE_RATIO = 0.5F;
    private static final float RING_FILL = 0.33F;
    private static final float RING_INNER = 0.42F;
    private static final float RING_OUTER = 0.62F;
    private static final float RING_OUTLINE = 0.07F;
    private static final int RING_SEGMENTS = 40;
    /** Bright yellow core, dark outline: the outline keeps it visible on sand, snow and grass, the core in caves. */
    private static final int COLOR = ARGB.color(255, 255, 238, 40);
    private static final int OUTLINE_COLOR = ARGB.color(235, 20, 20, 20);
    private static final int FILL_COLOR = ARGB.color(80, 255, 238, 40);

    private FootballPreviewRenderer() {
    }

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(FootballPreviewRenderer::collect);
    }

    private static void collect(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        BallPhysics.Trajectory trajectory = FootballPreview.compute(minecraft, partialTick);
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
        // Dashes: draw every other tick segment as a camera-facing ribbon with a dark outline strip on each side.
        for (int i = 0; i + 1 < points.size(); i += 2) {
            Vec3 a = points.get(i).subtract(cam);
            Vec3 b = points.get(i + 1).subtract(cam);
            Vec3 dir = b.subtract(a);
            Vec3 mid = a.add(b).scale(0.5);
            Vec3 side = dir.cross(mid);
            if (side.lengthSqr() < 1.0E-9) {
                continue;
            }
            float scale = Mth.clamp((float) mid.length() / DASH_SCALE_DISTANCE, 1.0F, DASH_MAX_SCALE);
            Vec3 unit = side.normalize();
            Vec3 core = unit.scale(DASH_HALF_WIDTH * scale);
            Vec3 edge = unit.scale(DASH_HALF_WIDTH * scale * (1.0F + OUTLINE_RATIO));
            strip(pose, buffer, a, b, core, edge, OUTLINE_COLOR);
            strip(pose, buffer, a, b, edge.scale(-1.0), core.scale(-1.0), OUTLINE_COLOR);
            strip(pose, buffer, a, b, core.scale(-1.0), core, COLOR);
        }
        Vec3 landing = trajectory.landing();
        if (landing == null) {
            return;
        }
        Vec3 c = landing.subtract(cam).add(0.0, 0.03, 0.0);
        band(pose, buffer, c, 0.0F, RING_FILL, FILL_COLOR);
        band(pose, buffer, c, RING_FILL, RING_INNER, OUTLINE_COLOR);
        band(pose, buffer, c, RING_INNER, RING_OUTER, COLOR);
        band(pose, buffer, c, RING_OUTER, RING_OUTER + RING_OUTLINE, OUTLINE_COLOR);
    }

    /** A quad from {@code a} to {@code b} spanning from the {@code from} offset to the {@code to} offset across the path. */
    private static void strip(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 from, Vec3 to, int color) {
        vertex(pose, buffer, a.add(from), color);
        vertex(pose, buffer, a.add(to), color);
        vertex(pose, buffer, b.add(to), color);
        vertex(pose, buffer, b.add(from), color);
    }

    /** A flat ring segment set around {@code c} between the two radii (a disc when {@code r0} is 0). */
    private static void band(PoseStack.Pose pose, VertexConsumer buffer, Vec3 c, float r0, float r1, int color) {
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / RING_SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / RING_SEGMENTS;
            vertex(pose, buffer, c.add(Mth.cos(a0) * r0, 0.0, Mth.sin(a0) * r0), color);
            vertex(pose, buffer, c.add(Mth.cos(a0) * r1, 0.0, Mth.sin(a0) * r1), color);
            vertex(pose, buffer, c.add(Mth.cos(a1) * r1, 0.0, Mth.sin(a1) * r1), color);
            vertex(pose, buffer, c.add(Mth.cos(a1) * r0, 0.0, Mth.sin(a1) * r0), color);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 p, int color) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(color)
                .setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
