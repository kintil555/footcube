package com.kintil555.football.entity;

import com.kintil555.football.registry.ModEntities;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
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

    private static final double IMPACT_THRESHOLD = 0.1;
    private static final double IMPACT_TO_SQUASH = 1.0;
    private static final float MAX_SQUASH = 0.5F;
    private static final float MIN_STRETCH = -0.2F;
    private static final float SPRING_STIFFNESS = 0.42F;
    private static final float SPRING_DAMPING = 0.65F;

    /** Per-tick share of the gap to the server position that the visual position closes while flying (3-step smoothing). */
    private static final double FOLLOW_ALPHA = 1.0 / 3.0;
    /** Ticks before a landing at which the visual position starts catching up with the server position. */
    private static final double LANDING_WINDOW = 8.0;
    /** How fast the follow rate relaxes back to normal after it was raised for a landing (per tick). */
    private static final double FOLLOW_RELAX = 0.12;
    private static final double MIN_FALL_SPEED = 0.1;
    private static final double SNAP_DISTANCE_SQR = 16.0;

    private final Quaternionf orientation = new Quaternionf();
    private final Quaternionf orientationO = new Quaternionf();
    /** Angular velocity in radians per tick, world space. */
    private final Vector3f omega = new Vector3f();
    private final Vector3f squashAxis = new Vector3f(0.0F, 1.0F, 0.0F);
    private Vec3 lastVelocity = Vec3.ZERO;
    /** Velocity of the visible motion (position change per tick). Unlike the synced delta movement it stays correct while dribbling. */
    private Vec3 lastMotion = Vec3.ZERO;
    private Vec3 lastPos;
    private float squash;
    private float squashO;
    private float squashVelocity;
    /** Smoothed position that is actually drawn; the entity itself snaps to every server position. */
    private Vec3 visual;
    private Vec3 visualO;
    private double follow = FOLLOW_ALPHA;

    public void tick(FootballEntity ball) {
        Vec3 v = ball.getDeltaMovement();
        this.updateVisual(ball);
        Vec3 pos = this.visual;
        Vec3 motion = this.lastPos == null ? Vec3.ZERO : pos.subtract(this.lastPos);
        if (motion.lengthSqr() > 4.0) {
            motion = Vec3.ZERO;
        }
        this.lastPos = pos;
        boolean ground = ball.onGround();
        this.orientationO.set(this.orientation);
        this.squashO = this.squash;

        this.updateOmega(motion, ground);
        this.applyRotation();
        this.updateSquash(v);
        this.lastVelocity = v;
        this.lastMotion = motion;
    }

    /**
     * Exponential smoothing hides network jitter, but while the server position already bounced the visual ball is still
     * above the ground, so it seems to bounce in the air. Close to a landing the follow rate therefore rises smoothly to 1
     * (the visual ball reaches the ground exactly when the server ball does), then relaxes back to normal.
     */
    private void updateVisual(FootballEntity ball) {
        Vec3 pos = ball.position();
        if (this.visual == null || this.visual.distanceToSqr(pos) > SNAP_DISTANCE_SQR) {
            this.visual = pos;
            this.visualO = pos;
            this.follow = FOLLOW_ALPHA;
            return;
        }
        this.visualO = this.visual;
        double wanted = this.landingFollow(ball);
        this.follow = wanted >= this.follow ? wanted : Math.max(wanted, this.follow - FOLLOW_RELAX);
        this.visual = new Vec3(
                Mth.lerp(this.follow, this.visual.x, pos.x),
                Mth.lerp(this.follow, this.visual.y, pos.y),
                Mth.lerp(this.follow, this.visual.z, pos.z));
    }

    /** Follow rate wanted for this tick: normal in flight, rising to 1 as a falling ball nears the ground. */
    private double landingFollow(FootballEntity ball) {
        double fall = ball.yo - ball.getY();
        if (fall < MIN_FALL_SPEED) {
            return FOLLOW_ALPHA;
        }
        Vec3 from = ball.position();
        Vec3 to = from.add(0.0, -fall * LANDING_WINDOW, 0.0);
        var hit = ball.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ball));
        if (hit.getType() == HitResult.Type.MISS) {
            return FOLLOW_ALPHA;
        }
        double ticksToLanding = (from.y - hit.getLocation().y) / fall;
        double t = Mth.clamp((LANDING_WINDOW - ticksToLanding) / (LANDING_WINDOW - 1.0), 0.0, 1.0);
        return FOLLOW_ALPHA + (1.0 - FOLLOW_ALPHA) * t;
    }

    /** The smoothed position to draw this frame (entity position until the first tick has run). */
    public Vec3 getVisualPosition(FootballEntity ball, float partialTicks) {
        if (this.visual == null) {
            return new Vec3(Mth.lerp(partialTicks, ball.xo, ball.getX()), Mth.lerp(partialTicks, ball.yo, ball.getY()),
                    Mth.lerp(partialTicks, ball.zo, ball.getZ()));
        }
        return new Vec3(
                Mth.lerp(partialTicks, this.visualO.x, this.visual.x),
                Mth.lerp(partialTicks, this.visualO.y, this.visual.y),
                Mth.lerp(partialTicks, this.visualO.z, this.visual.z));
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
        Vec3 last = this.lastMotion;
        double prevSq = last.x * last.x + last.z * last.z;
        double nowSq = v.x * v.x + v.z * v.z;
        if (prevSq > 0.01 && nowSq > 0.01) {
            double turn = Math.atan2(last.x * v.z - last.z * v.x, last.x * v.x + last.z * v.z);
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
