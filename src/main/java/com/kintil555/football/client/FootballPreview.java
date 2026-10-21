package com.kintil555.football.client;

import com.kintil555.football.action.BallPhysics;

/** Client-side holder for the dashed trajectory preview (kick when the ball is at the feet, throw while holding it). */
public final class FootballPreview {
    private static volatile BallPhysics.Trajectory trajectory;

    private FootballPreview() {
    }

    public static void set(BallPhysics.Trajectory value) {
        trajectory = value;
    }

    public static void clear() {
        trajectory = null;
    }

    public static BallPhysics.Trajectory get() {
        return trajectory;
    }
}
