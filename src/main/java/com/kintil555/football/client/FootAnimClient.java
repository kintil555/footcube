package com.kintil555.football.client;

import com.kintil555.football.entity.FootballEntity;
import com.kintil555.football.net.FootAnimPayload;
import com.kintil555.football.net.FootAnimPayload.State;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Client side of the player body animations (hold / throw / fetch the ball with the foot / foot on the ball).
 * The server decides which animation runs ({@link FootAnimPayload}); this class samples the Bedrock keyframes
 * from {@code assets/football/animations} and stacks them over the vanilla player pose.
 *
 * <p>Every animation is a layer that fades in over the layers below it (cross-fade) and fades out when it ends,
 * so there is never a snap. While walking, the legs keep their vanilla walk cycle during hold / throw.
 */
public final class FootAnimClient {
    private static final float FADE_IN = 0.15F;
    private static final float THROW_FADE_IN = 0.04F;
    private static final float FADE_OUT = 0.2F;
    private static final int EXPIRE_TICKS = 80;
    private static final double HIP_Y = 12.0;
    /** Leg length in blocks, used to turn "ball is this far away" into a swing angle. */
    private static final double LEG_LENGTH = 0.75;
    private static final double BALL_RADIUS = 0.466;
    private static final double MAX_REACH_RAD = 0.8;
    /** The foot reaches out this far (radians, forward) on top of the keyframes while resting on the ball. */
    private static final double STEP_EXTRA_REACH = 0.35;
    private static final double MAX_AIM_YAW = Math.toRadians(110.0);
    /** Reach toward the ball fades out while the foot pulls the ball back (first 0.3 s of the fetch). */
    private static final double REACH_DECAY_SECONDS = 0.3;
    private static final float WALK_SPEED_TO_VANILLA = 6.0F;

    private static final int HEAD = 0;
    private static final int BODY = 1;
    private static final int R_ARM = 2;
    private static final int L_ARM = 3;
    private static final int R_LEG = 4;
    private static final int L_LEG = 5;

    private static BedrockAnimation hold;
    private static BedrockAnimation throwAnim;
    private static BedrockAnimation fetch;
    private static BedrockAnimation step;

    private static final Map<Integer, List<Layer>> LAYERS = new HashMap<>();

    private static final class Layer {
        final State state;
        final int leg;
        final float forward;
        final float left;
        float startAge = Float.NaN;
        float retireAge = Float.NaN;
        boolean retiring;
        int ticks;
        int retireTicks;

        Layer(State state, int leg, float forward, float left) {
            this.state = state;
            this.leg = leg;
            this.forward = forward;
            this.left = left;
        }
    }

    private FootAnimClient() {
    }

    public static void init() {
        hold = BedrockAnimation.load("/assets/football/animations/hold.json");
        throwAnim = BedrockAnimation.load("/assets/football/animations/throw.json");
        fetch = BedrockAnimation.load("/assets/football/animations/fetch.json");
        step = BedrockAnimation.load("/assets/football/animations/step.json");

        ClientPlayNetworking.registerGlobalReceiver(FootAnimPayload.TYPE, (payload, context) ->
                context.client().execute(() -> set(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(FootAnimClient::tick);
    }

    private static void set(FootAnimPayload p) {
        State state = State.byId(p.state());
        List<Layer> layers = LAYERS.get(p.playerId());
        if (state == State.NONE) {
            if (layers != null) {
                layers.forEach(l -> l.retiring = true);
            }
            return;
        }
        if (layers == null) {
            layers = new ArrayList<>();
            LAYERS.put(p.playerId(), layers);
        }
        if (!layers.isEmpty()) {
            Layer top = layers.get(layers.size() - 1);
            if (!top.retiring && top.state == state && state != State.THROW && state != State.FETCH) {
                return;
            }
        }
        layers.add(new Layer(state, p.leg(), p.forward(), p.left()));
    }

    private static void tick(Minecraft client) {
        if (client.level == null) {
            LAYERS.clear();
            return;
        }
        Iterator<Map.Entry<Integer, List<Layer>>> it = LAYERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, List<Layer>> e = it.next();
            Entity player = client.level.getEntity(e.getKey());
            List<Layer> layers = e.getValue();
            if (player == null || player.isRemoved()) {
                it.remove();
                continue;
            }
            layers.removeIf(l -> {
                l.ticks++;
                if (l.retiring) {
                    l.retireTicks++;
                }
                if (l.retiring && l.retireTicks > FADE_OUT * 20 + 2) {
                    return true;
                }
                return switch (l.state) {
                    case THROW -> l.ticks > duration(throwAnim) * 20 + 2;
                    case FETCH -> l.ticks > EXPIRE_TICKS;
                    case HOLD -> l.ticks > 2 && !l.retiring && client.level.getEntitiesOfClass(FootballEntity.class,
                            player.getBoundingBox().inflate(4.0)).stream().noneMatch(b -> b.getHolderId() == e.getKey());
                    default -> false;
                };
            });
            // Layers under a fully faded-in top layer are no longer visible: drop them.
            while (layers.size() > 1 && !layers.get(layers.size() - 1).retiring
                    && layers.get(layers.size() - 1).ticks > 5) {
                layers.remove(0);
            }
            if (layers.isEmpty()) {
                it.remove();
            }
        }
    }

    private static double duration(BedrockAnimation a) {
        return a == null ? 0.0 : a.length;
    }

    private static BedrockAnimation animFor(State state) {
        return switch (state) {
            case HOLD -> hold;
            case THROW -> throwAnim;
            case FETCH -> fetch;
            case STEP -> step;
            default -> null;
        };
    }

    /** Called from the PlayerModel mixin after vanilla posed the model. */
    public static void apply(PlayerModel model, AvatarRenderState state) {
        List<Layer> layers = LAYERS.get(state.id);
        if (layers == null || layers.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == state.id && mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        float[][] vanillaRot = new float[6][];
        float[][] vanillaPos = new float[6][];
        float[][] curRot = new float[6][];
        float[][] curPos = new float[6][];
        for (int i = 0; i < 6; i++) {
            vanillaRot[i] = new float[] {parts[i].xRot, parts[i].yRot, parts[i].zRot};
            vanillaPos[i] = new float[] {parts[i].x, parts[i].y, parts[i].z};
            curRot[i] = vanillaRot[i].clone();
            curPos[i] = vanillaPos[i].clone();
        }
        float age = state.ageInTicks;
        float legVanilla = Mth.clamp(state.walkAnimationSpeed * WALK_SPEED_TO_VANILLA, 0.0F, 1.0F);

        for (Layer layer : layers) {
            if (Float.isNaN(layer.startAge)) {
                layer.startAge = age;
            }
            if (layer.retiring && Float.isNaN(layer.retireAge)) {
                layer.retireAge = age;
            }
            float w = weight(layer, age);
            if (w > 0.0F) {
                blend(layer, age, w, legVanilla, vanillaRot, vanillaPos, curRot, curPos);
            }
        }
        for (int i = 0; i < 6; i++) {
            parts[i].xRot = curRot[i][0];
            parts[i].yRot = curRot[i][1];
            parts[i].zRot = curRot[i][2];
            parts[i].x = curPos[i][0];
            parts[i].y = curPos[i][1];
            parts[i].z = curPos[i][2];
        }
    }

    private static float weight(Layer l, float age) {
        float t = (age - l.startAge) / 20.0F;
        float fadeIn = l.state == State.THROW ? THROW_FADE_IN : FADE_IN;
        float w = Mth.clamp(t / fadeIn, 0.0F, 1.0F);
        if (l.state == State.THROW && throwAnim != null) {
            w = Math.min(w, Mth.clamp((float) (throwAnim.length - t) / FADE_OUT, 0.0F, 1.0F));
        }
        if (l.retiring) {
            w = Math.min(w, 1.0F - Mth.clamp((age - l.retireAge) / 20.0F / FADE_OUT, 0.0F, 1.0F));
        }
        return smooth(w);
    }

    private static float smooth(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    /**
     * Samples {@code layer} and fades the current pose toward it by {@code weight}. The sampled target is built from the
     * untouched vanilla pose, so channels the layer does not animate fade back to vanilla.
     */
    private static void blend(Layer layer, float age, float weight, float legVanilla,
                              float[][] vanillaRot, float[][] vanillaPos, float[][] curRot, float[][] curPos) {
        BedrockAnimation anim = animFor(layer.state);
        if (anim == null) {
            return;
        }
        double t = Math.max(0.0, (age - layer.startAge) / 20.0);
        boolean foot = layer.state == State.FETCH || layer.state == State.STEP;
        boolean mirror = layer.leg == 1 && foot;
        boolean walkBlend = layer.state == State.HOLD || layer.state == State.THROW;

        double[] waist = anim.rotation("waist", t);
        double[] root = anim.position("root", t);
        double waistX = Math.toRadians(waist[0]);
        double waistY = Math.toRadians(-waist[1]);
        double waistZ = Math.toRadians(-waist[2]);

        String[] bones = new String[6];
        bones[HEAD] = "head";
        bones[BODY] = "waist";
        bones[R_ARM] = mirror ? "left_arm" : "right_arm";
        bones[L_ARM] = mirror ? "right_arm" : "left_arm";
        bones[R_LEG] = mirror ? "left_leg" : "right_leg";
        bones[L_LEG] = mirror ? "right_leg" : "left_leg";

        for (int i = 0; i < 6; i++) {
            boolean leg = i == R_LEG || i == L_LEG;
            boolean upper = i == HEAD || i == R_ARM || i == L_ARM;
            // Legs keep the vanilla walk cycle while walking with the ball in hand.
            float w = leg && walkBlend ? weight * (1.0F - legVanilla) : weight;

            double rx = vanillaRot[i][0];
            double ry = vanillaRot[i][1];
            double rz = vanillaRot[i][2];
            if (i != BODY && anim.hasRotation(bones[i])) {
                double[] r = anim.rotation(bones[i], t);
                double m = mirror ? -1.0 : 1.0;
                rx = Math.toRadians(r[0]);
                ry = Math.toRadians(-r[1]) * m;
                rz = Math.toRadians(-r[2]) * m;
            }
            if (i == BODY || upper) {
                rx += waistX;
                ry += waistY;
                rz += waistZ;
            }

            double px = vanillaPos[i][0];
            double py = vanillaPos[i][1];
            double pz = vanillaPos[i][2];
            if (i != BODY && anim.hasPosition(bones[i])) {
                double[] p = anim.position(bones[i], t);
                px += mirror ? -p[0] : p[0];
                py += -p[1];
                pz += -p[2];
            }
            if (i == BODY || upper) {
                double yy = py - HIP_Y;
                double ny = HIP_Y + yy * Math.cos(waistX) - pz * Math.sin(waistX);
                double nz = yy * Math.sin(waistX) + pz * Math.cos(waistX);
                py = ny;
                pz = nz;
            }
            px += root[0];
            py += -root[1];
            pz += -root[2];

            boolean stepping = foot && (layer.leg == 0 ? i == R_LEG : i == L_LEG);
            if (stepping) {
                if (layer.state == State.FETCH) {
                    // Aim the swing plane at the ball and stretch the leg toward it; the stretch fades as the foot pulls back.
                    double dist = Math.hypot(layer.forward, layer.left);
                    double bearing = Math.atan2(layer.left, layer.forward);
                    double reach = Mth.clamp((dist - BALL_RADIUS) / LEG_LENGTH, 0.0, MAX_REACH_RAD);
                    double u = Mth.clamp(t / REACH_DECAY_SECONDS, 0.0, 1.0);
                    double decay = 1.0 - u * u * (3.0 - 2.0 * u);
                    ry += -Mth.clamp(bearing, -MAX_AIM_YAW, MAX_AIM_YAW);
                    rx += -reach * decay;
                } else {
                    rx += -STEP_EXTRA_REACH;
                }
            }

            curRot[i][0] = Mth.lerp(w, curRot[i][0], (float) rx);
            curRot[i][1] = Mth.lerp(w, curRot[i][1], (float) ry);
            curRot[i][2] = Mth.lerp(w, curRot[i][2], (float) rz);
            curPos[i][0] = Mth.lerp(w, curPos[i][0], (float) px);
            curPos[i][1] = Mth.lerp(w, curPos[i][1], (float) py);
            curPos[i][2] = Mth.lerp(w, curPos[i][2], (float) pz);
        }
    }
}
