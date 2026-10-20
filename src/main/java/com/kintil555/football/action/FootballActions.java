package com.kintil555.football.action;

import com.kintil555.football.entity.FootballEntity;
import com.kintil555.football.net.FootballActionPayload;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-side implementation of every ball action a player can trigger. */
public final class FootballActions {
    private static final double REACH = 2.4;
    private static final double JUGGLE_REACH_XZ = 1.4;

    private static final double KICK_SPEED = 0.95;
    private static final double LONG_KICK_SPEED = 1.5;
    private static final double THROW_SPEED = 0.7;
    private static final double FEINT_BALL_SPEED = 0.5;
    private static final double FEINT_PLAYER_DASH = 0.4;
    private static final double JUGGLE_LIFT = 0.5;

    private static final int ACTION_COOLDOWN_TICKS = 4;
    private static final int ACTION_CONTROL_TICKS = 50;

    private FootballActions() {
    }

    public static void handle(ServerPlayer player, FootballActionPayload payload) {
        FootballAction action = FootballAction.byId(payload.actionId());
        if (action == null || player.isSpectator()) {
            return;
        }
        FootballEntity ball = findBall(player);
        if (ball == null) {
            return;
        }
        int side = Mth.clamp(payload.side(), -1, 1);
        switch (action) {
            case DRIBBLE -> ball.startDribble(player);
            case KICK -> kick(player, ball, false);
            case LONG_KICK -> kick(player, ball, true);
            case THROW -> throwIn(player, ball);
            case JUGGLE -> juggle(player, ball);
            case FEINT -> feint(player, ball, side);
        }
    }

    private static FootballEntity findBall(ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(REACH, 1.5, REACH);
        List<FootballEntity> balls = player.level().getEntitiesOfClass(FootballEntity.class, area);
        return balls.stream()
                .filter(ball -> ball.distanceToSqr(player) <= REACH * REACH)
                .min(Comparator.comparingDouble(ball -> ball.distanceToSqr(player)))
                .orElse(null);
    }

    private static void kick(ServerPlayer player, FootballEntity ball, boolean longKick) {
        if (!ball.tryBeginAction(ACTION_COOLDOWN_TICKS)) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 flat = FootballEntity.flatLook(player);
        double speed = longKick ? LONG_KICK_SPEED : KICK_SPEED;
        double lift = longKick
                ? Mth.clamp(0.4 - look.y * 0.4, 0.3, 0.85)
                : Mth.clamp(0.08 + look.y * 0.5, 0.04, 0.45);
        ball.launch(new Vec3(flat.x * speed, lift, flat.z * speed));
        ball.setController(player, ACTION_CONTROL_TICKS);
        playKickSound(player, ball, longKick ? 0.8F : 1.0F);
    }

    private static void throwIn(ServerPlayer player, FootballEntity ball) {
        if (!ball.tryBeginAction(ACTION_COOLDOWN_TICKS)) {
            return;
        }
        Vec3 flat = FootballEntity.flatLook(player);
        Vec3 hands = player.getEyePosition().add(flat.scale(0.5));
        ball.setPos(hands.x, hands.y - ball.getBbHeight() / 2.0, hands.z);
        ball.launch(new Vec3(flat.x * THROW_SPEED, 0.45, flat.z * THROW_SPEED));
        ball.setController(player, ACTION_CONTROL_TICKS);
        playKickSound(player, ball, 1.2F);
    }

    private static void juggle(ServerPlayer player, FootballEntity ball) {
        double dx = ball.getX() - player.getX();
        double dz = ball.getZ() - player.getZ();
        double dy = ball.getY() - player.getY();
        if (dx * dx + dz * dz > JUGGLE_REACH_XZ * JUGGLE_REACH_XZ || dy < -0.4 || dy > 1.4) {
            return;
        }
        if (!ball.tryBeginAction(ACTION_COOLDOWN_TICKS)) {
            return;
        }
        Vec3 flat = FootballEntity.flatLook(player);
        double homeX = player.getX() + flat.x * 0.25;
        double homeZ = player.getZ() + flat.z * 0.25;
        double vx = Mth.clamp((homeX - ball.getX()) * 0.25, -0.25, 0.25);
        double vz = Mth.clamp((homeZ - ball.getZ()) * 0.25, -0.25, 0.25);
        ball.launch(new Vec3(vx, JUGGLE_LIFT, vz));
        ball.setController(player, ACTION_CONTROL_TICKS);
        player.sendOverlayMessage(Component.translatable("message.football.juggle", ball.registerJuggle(player)));
        playKickSound(player, ball, 1.5F);
    }

    private static void feint(ServerPlayer player, FootballEntity ball, int side) {
        if (side == 0 || !ball.tryBeginAction(ACTION_COOLDOWN_TICKS)) {
            return;
        }
        Vec3 flat = FootballEntity.flatLook(player);
        Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
        ball.launch(new Vec3(
                right.x * side * FEINT_BALL_SPEED + flat.x * 0.1,
                0.05,
                right.z * side * FEINT_BALL_SPEED + flat.z * 0.1));
        ball.setController(player, ACTION_CONTROL_TICKS);
        player.push(right.x * side * FEINT_PLAYER_DASH, 0.0, right.z * side * FEINT_PLAYER_DASH);
        player.hurtMarked = true;
        playKickSound(player, ball, 1.3F);
    }

    private static void playKickSound(ServerPlayer player, FootballEntity ball, float pitch) {
        player.level().playSound(null, ball.getX(), ball.getY(), ball.getZ(),
                SoundEvents.SLIME_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, pitch);
    }
}
