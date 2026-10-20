package com.kintil555.football.client;

import com.kintil555.football.FootballMod;
import com.kintil555.football.entity.FootballEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/**
 * Draws, for every player interacting with a ball, a camera-facing bobbing pin above the head and a
 * circular area around the feet whose wall fades out at 1/3 block height. Everything uses the emissive
 * translucent render type with an upward normal, so it is never darkened by lighting or face shading.
 */
public final class FootballMarkerRenderer {
    private static final Identifier PIN_TEXTURE = Identifier.fromNamespaceAndPath(FootballMod.MOD_ID, "textures/misc/point.png");
    private static final Identifier WHITE_TEXTURE = Identifier.fromNamespaceAndPath(FootballMod.MOD_ID, "textures/misc/white.png");

    private static final float PIN_HALF_SIZE = 0.3F;
    private static final float PIN_HEIGHT_ABOVE_HEAD = 0.55F;
    private static final float BOB_AMPLITUDE = 0.1F;
    private static final float BOB_SPEED = 0.25F;

    private static final float AREA_RADIUS = 0.9F;
    private static final float AREA_WALL_HEIGHT = 1.0F / 3.0F;
    private static final float AREA_LIFT = 0.02F;
    private static final int AREA_SEGMENTS = 40;
    private static final int AREA_FLOOR_ALPHA = 45;
    private static final int AREA_WALL_ALPHA = 150;

    private static final int COLOR_R = 255;
    private static final int COLOR_G = 244;
    private static final int COLOR_B = 196;

    private FootballMarkerRenderer() {
    }

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(FootballMarkerRenderer::collect);
    }

    private static void collect(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        CameraRenderState camera = context.levelState().cameraRenderState;
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        Set<Integer> drawn = new HashSet<>();

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof FootballEntity ball)) {
                continue;
            }
            int controllerId = ball.getControllerId();
            if (controllerId < 0 || !drawn.add(controllerId)) {
                continue;
            }
            if (level.getEntity(controllerId) instanceof Player player && !player.isRemoved()) {
                boolean ownPlayerInFirstPerson = firstPerson && player == minecraft.getCameraEntity();
                draw(context, camera, level, player, partialTick, !ownPlayerInFirstPerson);
            }
        }
    }

    private static void draw(LevelRenderContext context, CameraRenderState camera, ClientLevel level,
                             Player player, float partialTick, boolean drawPin) {
        double x = Mth.lerp(partialTick, player.xo, player.getX()) - camera.pos.x;
        double y = Mth.lerp(partialTick, player.yo, player.getY()) - camera.pos.y;
        double z = Mth.lerp(partialTick, player.zo, player.getZ()) - camera.pos.z;
        float time = level.getGameTime() + partialTick;

        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(WHITE_TEXTURE, false), FootballMarkerRenderer::buildArea);
        if (drawPin) {
            float centerY = player.getBbHeight() + PIN_HEIGHT_ABOVE_HEAD + Mth.sin(time * BOB_SPEED) * BOB_AMPLITUDE;
            Vector3f right = new Vector3f(PIN_HALF_SIZE, 0.0F, 0.0F).rotate(camera.orientation);
            Vector3f up = new Vector3f(0.0F, PIN_HALF_SIZE, 0.0F).rotate(camera.orientation);
            RenderType pinType = RenderTypes.entityTranslucentEmissive(PIN_TEXTURE, false);
            collector.submitCustomGeometry(poseStack, pinType, (pose, buffer) -> buildPin(pose, buffer, centerY, right, up));
        }
        poseStack.popPose();
    }

    private static void buildPin(PoseStack.Pose pose, VertexConsumer buffer, float centerY, Vector3f right, Vector3f up) {
        int white = ARGB.color(255, 255, 255, 255);
        vertex(pose, buffer, -right.x - up.x, centerY - right.y - up.y, -right.z - up.z, 0.0F, 1.0F, white);
        vertex(pose, buffer, right.x - up.x, centerY + right.y - up.y, right.z - up.z, 1.0F, 1.0F, white);
        vertex(pose, buffer, right.x + up.x, centerY + right.y + up.y, right.z + up.z, 1.0F, 0.0F, white);
        vertex(pose, buffer, -right.x + up.x, centerY - right.y + up.y, -right.z + up.z, 0.0F, 0.0F, white);
    }

    private static void buildArea(PoseStack.Pose pose, VertexConsumer buffer) {
        int floor = ARGB.color(AREA_FLOOR_ALPHA, COLOR_R, COLOR_G, COLOR_B);
        int wallBottom = ARGB.color(AREA_WALL_ALPHA, COLOR_R, COLOR_G, COLOR_B);
        int wallTop = ARGB.color(0, COLOR_R, COLOR_G, COLOR_B);
        float bottomY = AREA_LIFT;
        float topY = AREA_LIFT + AREA_WALL_HEIGHT;

        for (int i = 0; i < AREA_SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / AREA_SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / AREA_SEGMENTS;
            float x0 = Mth.cos(a0) * AREA_RADIUS;
            float z0 = Mth.sin(a0) * AREA_RADIUS;
            float x1 = Mth.cos(a1) * AREA_RADIUS;
            float z1 = Mth.sin(a1) * AREA_RADIUS;

            vertex(pose, buffer, 0.0F, bottomY, 0.0F, 0.5F, 0.5F, floor);
            vertex(pose, buffer, 0.0F, bottomY, 0.0F, 0.5F, 0.5F, floor);
            vertex(pose, buffer, x1, bottomY, z1, 0.5F, 0.5F, floor);
            vertex(pose, buffer, x0, bottomY, z0, 0.5F, 0.5F, floor);

            vertex(pose, buffer, x0, bottomY, z0, 0.5F, 0.5F, wallBottom);
            vertex(pose, buffer, x1, bottomY, z1, 0.5F, 0.5F, wallBottom);
            vertex(pose, buffer, x1, topY, z1, 0.5F, 0.5F, wallTop);
            vertex(pose, buffer, x0, topY, z0, 0.5F, 0.5F, wallTop);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
