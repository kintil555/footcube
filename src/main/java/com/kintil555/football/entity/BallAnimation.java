package com.kintil555.football.entity;

import com.kintil555.football.registry.ModEntities;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Client-side procedural animation for the football: the rotation follows the ball's momentum (rolling without
 * slipping on the ground, free tumble in the air), and impacts squash the ball along the impact direction.
 * Pure math, so it is safe to live next to the entity; it is only ticked on the client.
 */
public class BallAnimation {
    private static final float RADIUS = ModEntities.BALL_SIZE / 2.0F;
    /** Mob gravity per tick; added back to the velocity change so plain falling is not read as an impact. */
    private static final double GRAVITY = 0.08;

    private static final float ROLL_BLEND = 0.5F;
    private static final float AIR_TUMBLE_BLEND = 0.04F;
    private static final float AIR_TUMBLE_RATIO = 0.5F;
    private static final float AIR_SPIN_DECAY = 0.985F;
    private static final float GROUND_YAW_DECAY = 0.9F;
    private static final float CURL_SPIN_SCALE = 6.0F;
    private static final float MAX_OMEGA = 1.2F;

    private static final double IMPACT_THRESHOLD = 0.12;
    private static final double IMPACT_TO_SQUASH = 0.4;
    private static final float MAX_SQUASH = 0.3F;
    private static final float MIN_STRETCH = -0.12F;
    private static final float SPRING_STIFFNESS = 0.5F;
    private static final float SPRING_DAMPING = 0.6F;

    private final Quaternionf orientation = new Quaternionf();
    private final Quaternionf orientationO = new Quaternionf();
    /** Angular velocity in radians per tick, world space. */
    private final Vector3f omega = new Vector3f();
    private final Vector3f squashAxis = new Vector3f(0.0F, 1.0F, 0.0F);
    private Vec3 lastVelocity = Vec3.ZERO;
    private float squash;
    private float squashO;
    private float squashVelocity;

    public void tick(FootballEntity ball) {
        Vec3 v = ball.getDeltaMovement();
        boolean ground = ball.onGround();
        this.orientationO.set(this.orientation);
        this.squashO = this.squash;

        this.updateOmega(v, ground);
        this.applyRotation();
        this.updateSquash(v);
        this.lastVelocity = v;
    }

    private void updateOmega(Vec3 v, boolean ground) {
        // Rolling without slipping: omega = up x v / r.
        float rollX = (float) v.z / RADIUS;
        float rollZ = (float) -v.x / RADIUS;
        if (ground) {
            this.omega.x += (rollX - this.omega.x) * ROLL_BLEND;
            this.omega.z += (rollZ - this.omega.z) * ROLL_BLEND;
            this.omega.y *= GROUND_YAW_DECAY;
        } else {
            // Keep the momentum, drift slowly toward a gentle tumble that matches the flight direction.
            this.omega.mul(AIR_SPIN_DECAY);
            this.omega.x += (rollX * AIR_TUMBLE_RATIO - this.omega.x) * AIR_TUMBLE_BLEND;
            this.omega.z += (rollZ * AIR_TUMBLE_RATIO - this.omega.z) * AIR_TUMBLE_BLEND;
        }
        // Curl: a turning flight path adds side spin around the vertical axis.
        double prevSq = this.lastVelocity.x * this.lastVelocity.x + this.lastVelocity.z * this.lastVelocity.z;
        double nowSq = v.x * v.x + v.z * v.z;
        if (prevSq > 0.01 && nowSq > 0.01) {
            double turn = Math.atan2(this.lastVelocity.x * v.z - this.lastVelocity.z * v.x,
                    this.lastVelocity.x * v.x + this.lastVelocity.z * v.z);
            turn = Mth.clamp(turn, -0.3, 0.3);
            this.omega.y += ((float) turn * CURL_SPIN_SCALE - this.omega.y) * 0.3F;
        }
        float len = this.omega.length();
        if (len > MAX_OMEGA) {
            this.omega.mul(MAX_OMEGA / len);
        }
    }

    private void applyRotation() {
        float angle = this.omega.length();
        if (angle < 1.0E-4F) {
            return;
        }
        Quaternionf step = new Quaternionf().rotationAxis(angle, this.omega.x / angle, this.omega.y / angle, this.omega.z / angle);
        step.mul(this.orientation, this.orientation);
        this.orientation.normalize();
    }

    private void updateSquash(Vec3 v) {
        double ax = v.x - this.lastVelocity.x;
        double ay = v.y - this.lastVelocity.y + GRAVITY;
        double az = v.z - this.lastVelocity.z;
        double impact = Math.sqrt(ax * ax + ay * ay + az * az);
        if (impact > IMPACT_THRESHOLD) {
            float target = (float) Math.min(MAX_SQUASH, impact * IMPACT_TO_SQUASH);
            if (target > this.squash) {
                this.squash = target;
                this.squashVelocity = 0.0F;
                this.squashAxis.set((float) (ax / impact), (float) (ay / impact), (float) (az / impact));
            }
        }
        // Damped spring back to round, with a small overshoot (stretch).
        this.squashVelocity = this.squashVelocity * SPRING_DAMPING - this.squash * SPRING_STIFFNESS;
        this.squash = Mth.clamp(this.squash + this.squashVelocity, MIN_STRETCH, MAX_SQUASH);
    }

    public void getOrientation(float partialTicks, Quaternionf dest) {
        this.orientationO.nlerp(this.orientation, partialTicks, dest);
    }

    public float getSquash(float partialTicks) {
        return Mth.lerp(partialTicks, this.squashO, this.squash);
    }

    public Vector3f getSquashAxis() {
        return this.squashAxis;
    }
}
