package com.kintil555.football.client;

import com.kintil555.football.entity.FootballEntity;
import com.kintil555.football.net.FootAnimPayload;
import com.kintil555.football.net.FootAnimPayload.State;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Client side of the player body animations (hold / throw / fetch the ball with the foot / foot on the ball).
 * The server decides which animation runs ({@link FootAnimPayload}); this class samples the Bedrock keyframes
 * from {@code assets/football/animations} and layers them over the vanilla player pose.
 */
public final class FootAnimClient {
    private static final float FADE_IN = 0.12F;
    private static final float THROW_FADE_OUT = 0.2F;
    private static final float CROSSFADE = 0.15F;
    private static final int EXPIRE_TICKS = 60;
    private static final double HIP_Y = 12.0;
    /** Extra sideways tilt (degrees) of the stepping leg when the ball lies fully to one side. */
    private static final double MAX_LEAN_DEG = 28.0;
    private static final double LEAN_REACH_PX = 2.5;

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

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();

    private static final class Entry {
        final State state;
        final int leg;
        final float lean;
        final Entry prev;
        float startAge = Float.NaN;
        int ticks;
        float prevFrozenAge = Float.NaN;

        Entry(State state, int leg, float lean, Entry prev) {
            this.state = state;
            this.leg = leg;
            this.lean = lean;
            this.prev = prev;
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
        Entry old = ENTRIES.get(p.playerId());
        if (state == State.NONE) {
            ENTRIES.remove(p.playerId());
            return;
        }
        if (old != null && old.state == state && state != State.THROW) {
            return;
        }
        Entry prev = old == null ? null : old.prev != null && old.ticks < 4 ? old.prev : old;
        ENTRIES.put(p.playerId(), new Entry(state, p.leg(), p.lean(), prev));
    }

    private static void tick(Minecraft client) {
        if (client.level == null) {
            ENTRIES.clear();
            return;
        }
        Iterator<Map.Entry<Integer, Entry>> it = ENTRIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Entry> e = it.next();
            Entry en = e.getValue();
            en.ticks++;
            if (en.prev != null && en.ticks > 8) {
                en.prev.prevFrozenAge = Float.NaN;
            }
            Entity player = client.level.getEntity(e.getKey());
            boolean drop = player == null || player.isRemoved();
            if (!drop) {
                switch (en.state) {
                    case THROW -> drop = en.ticks > duration(throwAnim) * 20 + 2;
                    case FETCH -> drop = en.ticks > EXPIRE_TICKS;
                    case HOLD -> drop = en.ticks > 2 && client.level.getEntitiesOfClass(FootballEntity.class,
                            player.getBoundingBox().inflate(4.0)).stream().noneMatch(b -> b.getHolderId() == e.getKey());
                    default -> {
                    }
                }
            }
            if (drop) {
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
        Entry entry = ENTRIES.get(state.id);
        if (entry == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == state.id && mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        float[][] baseRot = new float[6][];
        float[][] basePos = new float[6][];
        for (int i = 0; i < 6; i++) {
            baseRot[i] = new float[] {parts[i].xRot, parts[i].yRot, parts[i].zRot};
            basePos[i] = new float[] {parts[i].x, parts[i].y, parts[i].z};
        }
        float age = state.ageInTicks;
        float[][] outRot = new float[6][];
        float[][] outPos = new float[6][];
        for (int i = 0; i < 6; i++) {
            outRot[i] = baseRot[i].clone();
            outPos[i] = basePos[i].clone();
        }
        if (Float.isNaN(entry.startAge)) {
            entry.startAge = age;
        }
        // Cross-fade from the previous animation (frozen on its last sampled time) into the current one.
        if (entry.prev != null && !Float.isNaN(entry.prev.startAge) && entry.ticks <= 8) {
            float sinceStart = (age - entry.startAge) / 20.0F;
            float k = Mth.clamp(sinceStart / CROSSFADE, 0.0F, 1.0F);
            if (k < 1.0F) {
                if (Float.isNaN(entry.prev.prevFrozenAge)) {
                    entry.prev.prevFrozenAge = age;
                }
                float[][] pr = new float[6][];
                float[][] pp = new float[6][];
                layer(entry.prev, entry.prev.prevFrozenAge, baseRot, basePos, 1.0F, pr, pp);
                for (int i = 0; i < 6; i++) {
                    outRot[i] = pr[i];
                    outPos[i] = pp[i];
                }
                float[][] cr = new float[6][];
                float[][] cp = new float[6][];
                layer(entry, age, baseRot, basePos, 1.0F, cr, cp);
                for (int i = 0; i < 6; i++) {
                    for (int c = 0; c < 3; c++) {
                        outRot[i][c] = Mth.lerp(k, outRot[i][c], cr[i][c]);
                        outPos[i][c] = Mth.lerp(k, outPos[i][c], cp[i][c]);
                    }
                }
                write(parts, outRot, outPos);
                return;
            }
        }
        float w = weight(entry, age);
        layer(entry, age, baseRot, basePos, w, outRot, outPos);
        write(parts, outRot, outPos);
    }

    private static void write(ModelPart[] parts, float[][] rot, float[][] pos) {
        for (int i = 0; i < 6; i++) {
            parts[i].xRot = rot[i][0];
            parts[i].yRot = rot[i][1];
            parts[i].zRot = rot[i][2];
            parts[i].x = pos[i][0];
            parts[i].y = pos[i][1];
            parts[i].z = pos[i][2];
        }
    }

    private static float weight(Entry e, float age) {
        float t = (age - e.startAge) / 20.0F;
        float w = Mth.clamp(t / FADE_IN, 0.0F, 1.0F);
        if (e.state == State.THROW && throwAnim != null) {
            w = Math.min(w, Mth.clamp((float) (throwAnim.length - t) / THROW_FADE_OUT, 0.0F, 1.0F));
        }
        return w;
    }

    /** Samples {@code entry} at {@code age} and writes the blended pose (vanilla base -> animation) into out arrays. */
    private static void layer(Entry entry, float age, float[][] baseRot, float[][] basePos, float weight,
                              float[][] outRot, float[][] outPos) {
        BedrockAnimation anim = animFor(entry.state);
        for (int i = 0; i < 6; i++) {
            outRot[i] = baseRot[i].clone();
            outPos[i] = basePos[i].clone();
        }
        if (anim == null) {
            return;
        }
        double t = Math.max(0.0, (age - entry.startAge) / 20.0);
        boolean mirror = entry.leg == 1 && (entry.state == State.FETCH || entry.state == State.STEP);
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
            double rx = baseRot[i][0];
            double ry = baseRot[i][1];
            double rz = baseRot[i][2];
            boolean has = anim.hasRotation(bones[i]);
            if (has && i != BODY) {
                double[] r = anim.rotation(bones[i], t);
                rx = Math.toRadians(r[0]);
                ry = Math.toRadians(-r[1]) * (mirror ? -1.0 : 1.0);
                rz = Math.toRadians(-r[2]) * (mirror ? -1.0 : 1.0);
            }
            boolean upper = i == HEAD || i == R_ARM || i == L_ARM;
            if (i == BODY || upper) {
                rx += waistX;
                ry += waistY;
                rz += waistZ;
            }
            double px = basePos[i][0];
            double py = basePos[i][1];
            double pz = basePos[i][2];
            if (anim.hasPosition(bones[i]) && i != BODY) {
                double[] p = anim.position(bones[i], t);
                px += (mirror ? -p[0] : p[0]);
                py += -p[1];
                pz += -p[2];
            }
            if (upper || i == BODY) {
                double yy = py - HIP_Y;
                double ny = HIP_Y + yy * Math.cos(waistX) - pz * Math.sin(waistX);
                double nz = yy * Math.sin(waistX) + pz * Math.cos(waistX);
                py = ny;
                pz = nz;
            }
            // Root moves the whole body.
            px += root[0];
            py += -root[1];
            pz += -root[2];

            boolean stepping = (entry.state == State.FETCH || entry.state == State.STEP)
                    && (entry.leg == 0 ? i == R_LEG : i == L_LEG);
            if (stepping) {
                float lean = Mth.clamp(entry.lean, -1.0F, 1.0F);
                rz += Math.toRadians(-lean * MAX_LEAN_DEG);
                pz -= Math.abs(lean) * LEAN_REACH_PX;
            }
            outRot[i][0] = Mth.lerp(weight, baseRot[i][0], (float) rx);
            outRot[i][1] = Mth.lerp(weight, baseRot[i][1], (float) ry);
            outRot[i][2] = Mth.lerp(weight, baseRot[i][2], (float) rz);
            outPos[i][0] = Mth.lerp(weight, basePos[i][0], (float) px);
            outPos[i][1] = Mth.lerp(weight, basePos[i][1], (float) py);
            outPos[i][2] = Mth.lerp(weight, basePos[i][2], (float) pz);
        }
    }
}
