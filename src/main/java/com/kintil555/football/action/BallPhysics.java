package com.kintil555.football.action;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
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

    private BallPhysics() {
    }

    /** Kick follows the crosshair exactly; a flat aim still gets a small hop so the ball does not scrape the ground. */
    public static Vec3 kickVelocity(Vec3 look) {
        Vec3 v = look.normalize().scale(KICK_SPEED);
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
