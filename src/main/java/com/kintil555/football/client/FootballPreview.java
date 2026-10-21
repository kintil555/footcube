package com.kintil555.football.client;

import com.kintil555.football.action.BallPhysics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side holder for the dashed trajectory preview (kick when the ball is at the feet, throw while holding it).
 * The path is simulated once per tick; the renderer blends the previous and current tick's path by the frame's
 * partial tick, so the preview moves smoothly at frame rate instead of stepping at 20 Hz.
 */
public final class FootballPreview {
    private static volatile BallPhysics.Trajectory previous;
    private static volatile BallPhysics.Trajectory current;

    private FootballPreview() {
    }

    public static void set(BallPhysics.Trajectory value) {
        previous = current != null ? current : value;
        current = value;
    }

    public static void clear() {
        previous = null;
        current = null;
    }

    public static BallPhysics.Trajectory get() {
        return current;
    }

    /** The path blended between the last two ticks, or {@code null} when there is no preview. */
    public static BallPhysics.Trajectory interpolated(float partialTick) {
        BallPhysics.Trajectory cur = current;
        BallPhysics.Trajectory prev = previous;
        if (cur == null) {
            return null;
        }
        if (prev == null || prev == cur) {
            return cur;
        }
        List<Vec3> from = prev.points();
        List<Vec3> to = cur.points();
        List<Vec3> points = new ArrayList<>(to.size());
        for (int i = 0; i < to.size(); i++) {
            Vec3 a = from.get(Math.min(i, from.size() - 1));
            Vec3 b = to.get(i);
            points.add(lerp(partialTick, a, b));
        }
        Vec3 landing = cur.landing();
        if (landing != null && prev.landing() != null) {
            landing = lerp(partialTick, prev.landing(), landing);
        }
        return new BallPhysics.Trajectory(points, landing);
    }

    private static Vec3 lerp(float t, Vec3 a, Vec3 b) {
        return new Vec3(Mth.lerp(t, a.x, b.x), Mth.lerp(t, a.y, b.y), Mth.lerp(t, a.z, b.z));
    }
}
