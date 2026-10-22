package com.kintil555.football.action;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Kick strength: a plain click is the normal kick; holding left click charges it (capped), and a run-up toward the ball
 * adds momentum on top, like a penalty taker who starts a few steps back. Shared by the server (real kick) and the
 * client (trajectory preview).
 */
public final class KickPower {
    /** Holding this many ticks gives the strongest charge; holding longer changes nothing. */
    public static final int CHARGE_TICKS = 30;
    /** A click released within this many ticks is a normal (uncharged) kick. */
    public static final int TAP_TICKS = 6;
    /** Speed multiplier of a full charge. */
    public static final double MAX_CHARGE_FACTOR = 1.6;
    /** Extra speed (fraction) of a full-speed run-up straight at the kick direction. */
    public static final double MOMENTUM_BONUS = 0.7;
    /** Hard cap of the kick speed in blocks per tick, whatever charge and run-up. */
    public static final double MAX_SPEED = 3.0;
    /** Sprinting speed in blocks per tick: the run-up counts as full momentum at this speed. */
    public static final double SPRINT_SPEED = 0.28;

    private static final double BUILD_UP = 0.12;
    private static final double SLOW_DOWN = 0.35;

    private KickPower() {
    }

    /** Smoothed horizontal velocity of one player: builds up gradually while running, drops quickly when slowing. */
    public static final class RunUp {
        private double lastX;
        private double lastZ;
        private double vx;
        private double vz;
        private boolean started;

        /** Call once per tick with the player's position. */
        public void update(double x, double z) {
            if (!this.started) {
                this.started = true;
            } else {
                double rawX = x - this.lastX;
                double rawZ = z - this.lastZ;
                if (rawX * rawX + rawZ * rawZ > 4.0) {
                    rawX = 0.0;
                    rawZ = 0.0;
                }
                this.vx += (rawX - this.vx) * rate(rawX, rawZ, this.vx, this.vz);
                this.vz += (rawZ - this.vz) * rate(rawX, rawZ, this.vx, this.vz);
            }
            this.lastX = x;
            this.lastZ = z;
        }

        private static double rate(double rawX, double rawZ, double vx, double vz) {
            return rawX * rawX + rawZ * rawZ > vx * vx + vz * vz ? BUILD_UP : SLOW_DOWN;
        }

        /** 0..1 momentum along {@code direction} (horizontal part is used); squared so only a real run-up counts a lot. */
        public double momentum(Vec3 direction) {
            double len = Math.hypot(direction.x, direction.z);
            if (len < 1.0E-4) {
                return 0.0;
            }
            double along = (this.vx * direction.x + this.vz * direction.z) / len;
            double m = Mth.clamp(along / SPRINT_SPEED, 0.0, 1.0);
            return m * m;
        }
    }

    /** Kick speed in blocks per tick. {@code chargeTicks} 0 = normal click. */
    public static double speed(double momentum, int chargeTicks) {
        double charge = Mth.clamp(chargeTicks / (double) CHARGE_TICKS, 0.0, 1.0);
        double factor = 1.0 + (MAX_CHARGE_FACTOR - 1.0) * charge;
        return Math.min(MAX_SPEED, BallPhysics.KICK_SPEED * factor * (1.0 + MOMENTUM_BONUS * Mth.clamp(momentum, 0.0, 1.0)));
    }
}
