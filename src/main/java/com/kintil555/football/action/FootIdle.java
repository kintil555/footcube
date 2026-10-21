package com.kintil555.football.action;

import com.kintil555.football.entity.FootballEntity;
import com.kintil555.football.net.FootAnimPayload;
import com.kintil555.football.net.FootAnimPayload.State;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Idle foot animation: a player who stands still (turning around is fine) next to a resting ball for 2 seconds
 * starts the idle: if the ball is not at the ideal spot in front of the foot, the foot pulls it (the ball rolls to the
 * spot, leaning/stretching toward the side the ball is on), then the foot rests on the ball. Server-driven, the
 * clients only play the animation ({@link FootAnimPayload}).
 */
public final class FootIdle {
    private static final int IDLE_TICKS = 40;
    private static final double START_REACH = 2.2;
    private static final double MOVE_EPSILON = 0.015;
    private static final double BALL_REST_SPEED_SQR = 0.05 * 0.05;
    private static final double SPOT_FORWARD = 0.8;
    private static final double SPOT_SIDE = 0.12;
    /** Lateral offset of a leg's hip from the body center. */
    private static final double HIP_SIDE = 0.125;
    private static final double SPOT_TOLERANCE = 0.18;
    private static final double RE_FETCH_DISTANCE = 0.3;
    /** The foot touches the ball about here in the fetch animation (0.25 s). */
    private static final int FETCH_ROLL_START = 5;
    private static final int FETCH_MAX_TICKS = 30;

    private static final Map<UUID, St> STATES = new HashMap<>();

    private static final class St {
        Vec3 last;
        int still;
        State state = State.NONE;
        int stateTicks;
        int leg;
    }

    private FootIdle() {
    }

    public static void tick(MinecraftServer server) {
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            update(player, STATES.computeIfAbsent(player.getUUID(), id -> new St()));
        }
        STATES.keySet().retainAll(online);
    }

    /** Sends {@code state} for {@code player} to the player and everyone tracking them. */
    public static void broadcast(ServerPlayer player, State state) {
        broadcast(player, state, 0, 0.0F, 0.0F);
    }

    public static void broadcast(ServerPlayer player, State state, int leg, float forward, float left) {
        FootAnimPayload payload = FootAnimPayload.of(player.getId(), state, leg, forward, left);
        Set<ServerPlayer> targets = new HashSet<>(PlayerLookup.tracking(player));
        targets.add(player);
        for (ServerPlayer target : targets) {
            ServerPlayNetworking.send(target, payload);
        }
    }

    private static void update(ServerPlayer player, St st) {
        Vec3 pos = player.position();
        boolean moved = st.last == null
                || Math.hypot(pos.x - st.last.x, pos.z - st.last.z) > MOVE_EPSILON
                || !player.onGround() || player.isSpectator() || player.isPassenger() || !player.isAlive();
        st.last = pos;

        AABB area = player.getBoundingBox().inflate(START_REACH + 1.0, 1.5, START_REACH + 1.0);
        boolean holding = false;
        FootballEntity ball = null;
        for (FootballEntity b : player.level().getEntitiesOfClass(FootballEntity.class, area)) {
            if (b.getHolderId() == player.getId()) {
                holding = true;
                break;
            }
        }
        if (!holding) {
            boolean active = st.state == State.FETCH || st.state == State.STEP;
            ball = player.level().getEntitiesOfClass(FootballEntity.class, area).stream()
                    .filter(b -> !b.isHeld() && b.onGround() && Math.abs(b.getY() - player.getY()) < 1.0
                            && Math.hypot(b.getX() - pos.x, b.getZ() - pos.z) <= START_REACH
                            && (active || b.getDeltaMovement().horizontalDistanceSqr() < BALL_REST_SPEED_SQR))
                    .min(Comparator.comparingDouble(b -> b.distanceToSqr(player)))
                    .orElse(null);
        }
        if (holding || moved || ball == null) {
            reset(player, st);
            return;
        }

        st.still++;
        if (st.state == State.NONE) {
            if (st.still >= IDLE_TICKS) {
                beginFor(player, st, ball);
            }
            return;
        }
        Vec3 spot = spot(player, st.leg);
        double dist = Math.hypot(ball.getX() - spot.x, ball.getZ() - spot.z);
        st.stateTicks++;
        if (st.state == State.FETCH) {
            if (st.stateTicks >= FETCH_ROLL_START) {
                ball.rollTo(spot, 0.35);
            }
            if ((st.stateTicks > FETCH_ROLL_START && dist < SPOT_TOLERANCE) || st.stateTicks > FETCH_MAX_TICKS) {
                ball.rollTo(spot, 0.35);
                st.state = State.STEP;
                st.stateTicks = 0;
                broadcast(player, State.STEP, st.leg, 0.0F, 0.0F);
            }
        } else if (st.state == State.STEP) {
            if (dist > RE_FETCH_DISTANCE) {
                if (ball.getDeltaMovement().horizontalDistanceSqr() < BALL_REST_SPEED_SQR) {
                    beginFor(player, st, ball);
                } else {
                    reset(player, st);
                    st.still = 0;
                }
            } else if (dist > 0.04) {
                ball.rollTo(spot, 0.2);
            }
        }
    }

    /** Ball already under the foot: step on it; otherwise pull it there first. */
    private static void beginFor(ServerPlayer player, St st, FootballEntity ball) {
        double yaw = Math.toRadians(player.yBodyRot);
        double leftX = Math.cos(yaw);
        double leftZ = Math.sin(yaw);
        double lat = (ball.getX() - player.getX()) * leftX + (ball.getZ() - player.getZ()) * leftZ;
        st.leg = lat > 0.25 ? 1 : 0;
        st.stateTicks = 0;
        Vec3 spot = spot(player, st.leg);
        if (Math.hypot(ball.getX() - spot.x, ball.getZ() - spot.z) < SPOT_TOLERANCE) {
            st.state = State.STEP;
            broadcast(player, State.STEP, st.leg, 0.0F, 0.0F);
        } else {
            st.state = State.FETCH;
            double side = st.leg == 1 ? HIP_SIDE : -HIP_SIDE;
            double rx = ball.getX() - (player.getX() + leftX * side);
            double rz = ball.getZ() - (player.getZ() + leftZ * side);
            float forward = (float) (rx * -Math.sin(yaw) + rz * Math.cos(yaw));
            float left = (float) (rx * leftX + rz * leftZ);
            broadcast(player, State.FETCH, st.leg, forward, left);
        }
    }

    /** Ideal resting point of the ball: just in front of the stepping foot, relative to the body direction. */
    private static Vec3 spot(ServerPlayer player, int leg) {
        double yaw = Math.toRadians(player.yBodyRot);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double lx = Math.cos(yaw);
        double lz = Math.sin(yaw);
        double side = leg == 1 ? SPOT_SIDE : -SPOT_SIDE;
        return new Vec3(player.getX() + fx * SPOT_FORWARD + lx * side, player.getY(), player.getZ() + fz * SPOT_FORWARD + lz * side);
    }

    private static void reset(ServerPlayer player, St st) {
        st.still = 0;
        st.stateTicks = 0;
        if (st.state == State.FETCH || st.state == State.STEP) {
            broadcast(player, State.NONE);
        }
        st.state = State.NONE;
    }
}
