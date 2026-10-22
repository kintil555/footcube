package com.kintil555.football.action;

import java.util.ArrayList;
import java.util.List;
import com.kintil555.football.entity.FootballEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shared ball physics constants and a trajectory simulator (used for the dashed preview). The numbers match
 * LivingEntity#travelInAir for the ball attributes (air drag modifier 0.05, gravity 0.08). Bounces are not simulated.
 */
public final class BallPhysics {
    public static final double GRAVITY = 0.08;
    public static final double AIR_DRAG_MODIFIER = 0.05;
    public static final double DRAG_H = 1.0 - (1.0 - 0.91) * AIR_DRAG_MODIFIER;
    public static final double DRAG_V = 1.0 - (1.0 - 0.98) * AIR_DRAG_MODIFIER;

    public static final double KICK_SPEED = 1.35;
    public static final double KICK_MIN_LIFT = 0.12;

    /** Holding middle click this many ticks gives the strongest throw. */
    public static final int THROW_CHARGE_TICKS = 40;
    public static final double THROW_MIN_SPEED = 0.5;
    public static final double THROW_MAX_SPEED = 1.7;
    private static final double THROW_MIN_LIFT = 0.05;

    private static final int MAX_STEPS = 120;

    /** Middle click (catch / lift): the ball must be this close along the crosshair, and the crosshair must be on it. */
    public static final double GRAB_REACH = 1.8;
    /** Left click on an incoming shot (save). */
    public static final double SAVE_REACH = 2.6;
    /** Extra slack (blocks) around the ball when testing whether the crosshair is on it. */
    private static final double AIM_TOLERANCE = 0.1;
    /** Slower balls are kicked, not saved. */
    public static final double SAVE_MIN_SPEED = 0.5;

    private BallPhysics() {
    }

    /**
     * The free ball the crosshair is actually on, no farther than {@code reach} along the view ray (null if none). Works on
     * both sides, so the client can skip clicks the server would refuse.
     */
    public static FootballEntity findAimedBall(Player player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(reach));
        FootballEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (FootballEntity ball : player.level().getEntitiesOfClass(FootballEntity.class,
                player.getBoundingBox().inflate(reach + 1.0))) {
            if (ball.isHeld()) {
                continue;
            }
            var hit = ball.getBoundingBox().inflate(AIM_TOLERANCE).clip(eye, end);
            if (hit.isPresent()) {
                double d = eye.distanceToSqr(hit.get());
                if (d < bestDist) {
                    bestDist = d;
                    best = ball;
                }
            }
        }
        return best;
    }

    /** True when {@code ball} flies fast enough, toward {@code player}, to be saved. */
    public static boolean isIncomingShot(FootballEntity ball, Player player) {
        Vec3 v = ball.getDeltaMovement();
        if (v.lengthSqr() < SAVE_MIN_SPEED * SAVE_MIN_SPEED) {
            return false;
        }
        Vec3 toPlayer = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0).subtract(ball.position());
        return v.dot(toPlayer) > 0.0;
    }

    /** Kick follows the crosshair exactly; a flat aim still gets a small hop so the ball does not scrape the ground. */
    public static Vec3 kickVelocity(Vec3 look) {
        return kickVelocity(look, KICK_SPEED);
    }

    /** Same as {@link #kickVelocity(Vec3)} with an explicit speed (see {@link KickPower#speed}). */
    public static Vec3 kickVelocity(Vec3 look, double speed) {
        Vec3 v = look.normalize().scale(speed);
        return v.y < KICK_MIN_LIFT ? new Vec3(v.x, KICK_MIN_LIFT, v.z) : v;
    }

    /** Throw follows the look direction; {@code power} is 0..1 (how long middle click was held). */
    public static Vec3 throwVelocity(Vec3 look, double power) {
        double speed = Mth.lerp(Mth.clamp(power, 0.0, 1.0), THROW_MIN_SPEED, THROW_MAX_SPEED);
        Vec3 v = look.normalize().scale(speed);
        return v.y < THROW_MIN_LIFT ? new Vec3(v.x, THROW_MIN_LIFT, v.z) : v;
    }

    /** @param landing where the ball first touches a block, or {@code null} if it never lands within the simulated time. */
    public record Trajectory(List<Vec3> points, Vec3 landing) {
    }

    /** Simulates the ball center from {@code center} with velocity {@code vel} until it hits a block. */
    public static Trajectory simulate(Level level, Entity context, Vec3 center, Vec3 vel, double radius) {
        List<Vec3> points = new ArrayList<>();
        Vec3 pos = center;
        points.add(pos);
        for (int i = 0; i < MAX_STEPS; i++) {
            Vec3 next = pos.add(vel);
            // The lowest point of the ball is what touches the ground, so ray-cast from there.
            Vec3 from = pos.add(0.0, -radius, 0.0);
            Vec3 to = next.add(0.0, -radius, 0.0);
            var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 at = hit.getLocation();
                points.add(at.add(0.0, radius, 0.0));
                return new Trajectory(points, at);
            }
            pos = next;
            points.add(pos);
            vel = new Vec3(vel.x * DRAG_H, (vel.y - GRAVITY) * DRAG_V, vel.z * DRAG_H);
        }
        return new Trajectory(points, null);
    }
}
