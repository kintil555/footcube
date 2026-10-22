package com.kintil555.football.client;

import com.kintil555.football.action.BallPhysics;
import com.kintil555.football.action.KickPower;
import com.kintil555.football.entity.FootballEntity;
import com.kintil555.football.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side source of the dashed trajectory preview (kick when the ball is at the feet, throw while holding it).
 * The tick only records what to preview; the path itself is simulated every frame from the interpolated ball
 * position and view direction, so it follows the camera smoothly instead of stepping at the 20 Hz tick rate.
 */
public final class FootballPreview {
    private static volatile FootballEntity ball;
    private static volatile boolean thrown;
    private static volatile boolean charging;
    private static volatile int chargeTicks;
    private static volatile double kickMomentum;

    private FootballPreview() {
    }

    /** Previews a kick of {@code target}, which is at the player's feet. */
    public static void setKick(FootballEntity target, int charge, double momentum) {
        ball = target;
        thrown = false;
        charging = false;
        chargeTicks = charge;
        kickMomentum = momentum;
    }

    /** Previews a throw of the held {@code target} with the given charge. */
    public static void setThrow(FootballEntity target, int ticks, boolean isCharging) {
        ball = target;
        thrown = true;
        charging = isCharging;
        chargeTicks = ticks;
    }

    public static void clear() {
        ball = null;
    }

    /** Simulates the preview path for this frame, or returns {@code null} when there is nothing to preview. */
    public static BallPhysics.Trajectory compute(Minecraft minecraft, float partialTick) {
        FootballEntity target = ball;
        Player player = minecraft.player;
        if (target == null || target.isRemoved() || player == null || minecraft.level == null) {
            return null;
        }
        double halfSize = ModEntities.BALL_SIZE / 2.0;
        Vec3 look = player.getViewVector(partialTick);
        Vec3 start = target.getAnimation().getVisualPosition(target, partialTick).add(0.0, halfSize, 0.0);
        Vec3 velocity;
        if (thrown) {
            float charge = chargeTicks + (charging && chargeTicks < BallPhysics.THROW_CHARGE_TICKS ? partialTick : 0.0F);
            velocity = BallPhysics.throwVelocity(look, charge / (double) BallPhysics.THROW_CHARGE_TICKS);
        } else {
            velocity = BallPhysics.kickVelocity(look, KickPower.speed(kickMomentum, chargeTicks));
        }
        return BallPhysics.simulate(minecraft.level, player, start, velocity, halfSize);
    }
}
