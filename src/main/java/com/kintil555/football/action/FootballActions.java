package com.kintil555.football.action;

import com.kintil555.football.entity.FootballEntity;
import com.kintil555.football.net.FootballActionPayload;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-side implementation of every ball action a player can trigger. */
public final class FootballActions {
    private static final double REACH = 2.4;
    private static final double JUGGLE_REACH_XZ = 1.4;

    /** Holding SPACE this long gives the maximum kick (4 seconds). */
    public static final int MAX_CHARGE_TICKS = 80;
    private static final double KICK_MIN_SPEED = 0.35;
    private static final double KICK_MAX_SPEED = 2.8;
    private static final double KICK_MIN_LIFT = 0.06;
    private static final double KICK_MAX_LIFT = 1.2;

    private static final double NUTMEG_SLOW = 0.03;
    private static final int NUTMEG_SLOW_TICKS = 18;
    private static final Identifier SLOW_ID = Identifier.fromNamespaceAndPath("football", "nutmeg_slow");
    private static final Map<UUID, Long> SLOWED_UNTIL = new HashMap<>();
    private static final double MISS_CHANCE = 0.12;
    private static final double CHIP_RANGE = 5.0;

    private static final double THROW_SPEED = 0.8;
    private static final double FEINT_BALL_SPEED = 0.5;
    private static final double FEINT_PLAYER_DASH = 0.4;
    private static final double JUGGLE_LIFT = 0.5;
    private static final double PASS_MAX_RANGE = 40.0;

    private static final int ACTION_COOLDOWN_TICKS = 4;
    private static final int ACTION_CONTROL_TICKS = 50;

    private FootballActions() {
    }

    public static void handle(ServerPlayer player, FootballActionPayload payload) {
        FootballAction action = FootballAction.byId(payload.actionId());
        if (action == null || player.isSpectator()) {
            return;
        }
        int side = Mth.clamp(payload.side(), -1, 1);
        int forward = Mth.clamp(payload.forward(), -1, 1);

        if (action == FootballAction.PASS) {
            FootballEntity held = findHeldBall(player);
            if (held != null) {
                pass(player, held, payload.arg());
            }
            return;
        }

        FootballEntity ball = action == FootballAction.HOLD ? findHeldOrNearBall(player) : findBall(player);
        if (ball == null) {
            return;
        }
        switch (action) {
            case DRIBBLE -> ball.startDribble(player);
            case KICK -> kick(player, ball, side, forward, Mth.clamp(payload.arg(), 0, MAX_CHARGE_TICKS));
            case HOLD -> hold(player, ball);
            case JUGGLE -> juggle(player, ball);
            case FEINT -> feint(player, ball, side);
            default -> {
            }
        }
    }

    private static FootballEntity findBall(ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(REACH, 1.5, REACH);
        List<FootballEntity> balls = player.level().getEntitiesOfClass(FootballEntity.class, area);
        return balls.stream()
                .filter(ball -> !ball.isHeld())
                .filter(ball -> ball.distanceToSqr(player) <= REACH * REACH)
                .min(Comparator.comparingDouble(ball -> ball.distanceToSqr(player)))
                .orElse(null);
    }

    private static FootballEntity findHeldBall(ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(3.0);
        return player.level().getEntitiesOfClass(FootballEntity.class, area).stream()
                .filter(ball -> ball.getHolderId() == player.getId())
                .findFirst()
                .orElse(null);
    }

    private static FootballEntity findHeldOrNearBall(ServerPlayer player) {
        FootballEntity held = findHeldBall(player);
        return held != null ? held : findBall(player);
    }

    /**
     * Charged kick. Power grows linearly with the ticks SPACE was held (0..4 s). The direction follows
     * the WASD input (W + D kicks diagonally right, D alone kicks sideways right), falling back to the look direction.
     */
    private static void kick(ServerPlayer player, FootballEntity ball, int side, int forward, int charge) {
        if (!ball.tryBeginAction(ACTION_COOLDOWN_TICKS)) {
            return;
        }
        double power = charge / (double) MAX_CHARGE_TICKS;
        Vec3 look = player.getLookAngle();
        Vec3 flat = FootballEntity.flatLook(player);
        Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
        Vec3 dir = flat.scale(forward).add(right.scale(side));
        dir = dir.lengthSqr() < 1.0E-6 ? flat : dir.normalize();

        double speed = Mth.lerp(power, KICK_MIN_SPEED, KICK_MAX_SPEED);
        double lift = Mth.lerp(power, KICK_MIN_LIFT, KICK_MAX_LIFT) + Mth.clamp(look.y * 0.25, -0.05, 0.25);
        // Curl: random direction, stronger with power. The launch heading is pre-rotated against the curl so the
        // ball bends back toward the aim. Occasionally (MISS_CHANCE) the kick is pre-rotated wrongly and misses.
        var rng = player.getRandom();
        double omega = (rng.nextBoolean() ? 1 : -1) * Mth.lerp(power, 0.006, 0.016) * (0.7 + rng.nextDouble() * 0.6);
        double angle = -omega * FootballEntity.SPIN_TOTAL_FACTOR * 0.5;
        if (rng.nextDouble() < MISS_CHANCE) {
            angle += (rng.nextBoolean() ? 1 : -1) * (0.15 + rng.nextDouble() * 0.2) * (0.5 + power);
        }
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dx = dir.x * cos - dir.z * sin;
        double dz = dir.x * sin + dir.z * cos;
        ball.launchCurved(new Vec3(dx * speed, Math.max(0.03, lift), dz * speed), omega);
        ball.setController(player, ACTION_CONTROL_TICKS);
        playKickSound(player, ball, (float) Mth.lerp(power, 1.2, 0.7));
    }

    /** Middle click: first click lifts the ball above the head, second click throws it forward. */
    private static void hold(ServerPlayer player, FootballEntity ball) {
        if (!ball.tryBeginAction(ACTION_COOLDOWN_TICKS)) {
            return;
        }
        if (ball.getHolderId() == player.getId()) {
            Vec3 flat = FootballEntity.flatLook(player);
            Vec3 look = player.getLookAngle();
            ball.release();
            ball.launch(new Vec3(flat.x * THROW_SPEED, Mth.clamp(0.35 + look.y * 0.5, 0.15, 0.8), flat.z * THROW_SPEED));
            ball.setController(player, ACTION_CONTROL_TICKS);
            playKickSound(player, ball, 1.2F);
        } else if (!ball.isHeld()) {
            ball.grab(player);
            player.sendOverlayMessage(Component.translatable("message.football.hold"));
            playKickSound(player, ball, 1.6F);
        }
    }

    /** Throws the held ball to another player; the ball gently homes in on them while in the air. */
    private static void pass(ServerPlayer player, FootballEntity ball, int targetId) {
        Entity target = player.level().getEntity(targetId);
        if (!(target instanceof Player targetPlayer) || targetPlayer == player || targetPlayer.isSpectator()
                || targetPlayer.distanceToSqr(player) > PASS_MAX_RANGE * PASS_MAX_RANGE) {
            return;
        }
        if (!ball.tryBeginAction(ACTION_COOLDOWN_TICKS)) {
            return;
        }
        ball.release();
        Vec3 to = targetPlayer.position().add(0.0, targetPlayer.getBbHeight() * 0.6, 0.0).subtract(ball.position());
        double dist = Math.sqrt(to.x * to.x + to.z * to.z);
        double speed = Mth.clamp(0.35 + dist * 0.045, 0.4, 1.6);
        Vec3 flat = dist < 1.0E-4 ? FootballEntity.flatLook(player) : new Vec3(to.x / dist, 0.0, to.z / dist);
        ball.launch(new Vec3(flat.x * speed, Mth.clamp(0.25 + dist * 0.015, 0.25, 0.7), flat.z * speed));
        ball.setPassTarget(targetPlayer);
        ball.setController(player, ACTION_CONTROL_TICKS);
        playKickSound(player, ball, 1.3F);
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
        if (opponentAhead(player, flat)) {
            // Nutmeg: lob the ball over the opponent and dash past; they can still head it to take over.
            ball.launch(new Vec3(flat.x * 0.55, 0.7, flat.z * 0.55));
            ball.setController(player, ACTION_CONTROL_TICKS);
            player.push(flat.x * 0.45, 0.0, flat.z * 0.45);
            player.hurtMarked = true;
            applyNutmegSlow(player);
            playKickSound(player, ball, 1.7F);
            return;
        }
        double homeX = player.getX() + flat.x * 0.25;
        double homeZ = player.getZ() + flat.z * 0.25;
        double vx = Mth.clamp((homeX - ball.getX()) * 0.25, -0.25, 0.25);
        double vz = Mth.clamp((homeZ - ball.getZ()) * 0.25, -0.25, 0.25);
        ball.launch(new Vec3(vx, JUGGLE_LIFT, vz));
        ball.setController(player, ACTION_CONTROL_TICKS);
        player.sendOverlayMessage(Component.translatable("message.football.juggle", ball.registerJuggle(player)));
        playKickSound(player, ball, 1.5F);
    }

    /** The nutmeg costs the player 3% movement speed only during the move, about as long as the lobbed ball is in the air (transient, never saved). */
    private static void applyNutmegSlow(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.addOrUpdateTransientModifier(new AttributeModifier(
                SLOW_ID, -NUTMEG_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        SLOWED_UNTIL.put(player.getUUID(), player.level().getGameTime() + NUTMEG_SLOW_TICKS);
    }

    /** Called every server tick: removes expired slow modifiers. */
    public static void tickServer(MinecraftServer server) {
        if (SLOWED_UNTIL.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        SLOWED_UNTIL.entrySet().removeIf(entry -> {
            ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null) {
                return true;
            }
            if (now < entry.getValue()) {
                return false;
            }
            AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(SLOW_ID);
            }
            return true;
        });
    }

    private static boolean opponentAhead(ServerPlayer player, Vec3 flat) {
        AABB area = player.getBoundingBox().inflate(CHIP_RANGE, 1.5, CHIP_RANGE);
        for (Player other : player.level().getEntitiesOfClass(Player.class, area)) {
            if (other == player || other.isSpectator()) {
                continue;
            }
            Vec3 to = other.position().subtract(player.position());
            double dist = Math.sqrt(to.x * to.x + to.z * to.z);
            if (dist > 0.3 && dist <= CHIP_RANGE && (to.x * flat.x + to.z * flat.z) / dist > 0.6) {
                return true;
            }
        }
        return false;
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

    public static void playKickSound(Player player, FootballEntity ball, float pitch) {
        player.level().playSound(null, ball.getX(), ball.getY(), ball.getZ(),
                SoundEvents.SLIME_BLOCK_HIT, SoundSource.PLAYERS, 0.8F, pitch);
    }
}
