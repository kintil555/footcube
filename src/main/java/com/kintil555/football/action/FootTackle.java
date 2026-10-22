package com.kintil555.football.action;

import com.kintil555.football.entity.FootballEntity;
import com.kintil555.football.net.FootAnimPayload.State;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Tackle (R): the player in front of the tackler falls down for 4 seconds. A fallen player cannot move, jump, or touch a
 * ball, and drops any ball they hold or dribble. The fall is animated client side ({@link State#TACKLED}).
 *
 * <p>A mob that is tackled is tossed 2 blocks up, and once it lands it stays still (no AI) until the same 4 seconds
 * have passed since the tackle.
 */
public final class FootTackle {
    private static final double REACH = 2.6;
    private static final double MIN_FACING_DOT = 0.5;
    private static final int DOWN_TICKS = 80;
    private static final int COOLDOWN_TICKS = 30;
    private static final double KNOCK = 0.3;
    /** Initial upward speed that lifts a mob exactly 2 blocks (gravity 0.08, drag 0.98). */
    private static final double MOB_TOSS_SPEED = 0.5449;
    private static final double MOB_TOSS_PUSH = 0.1;
    private static final double MOB_MAX_HEIGHT = 3.0;
    private static final int MOB_MIN_AIR_TICKS = 3;
    private static final int MOB_MAX_AIR_TICKS = 60;
    /** Saved with the mob while it is frozen, so a restart or chunk reload can never leave it without AI. */
    public static final String STUN_TAG = "football_stunned";
    private static final Identifier DOWN_SPEED_ID = Identifier.fromNamespaceAndPath("football", "tackle_down_speed");
    private static final Identifier DOWN_JUMP_ID = Identifier.fromNamespaceAndPath("football", "tackle_down_jump");

    private static final Map<UUID, Long> DOWN_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    private static final Map<UUID, MobDown> MOB_DOWN = new HashMap<>();

    private static final class MobDown {
        final Mob mob;
        final long until;
        int ticks;
        boolean landed;
        boolean froze;

        MobDown(Mob mob, long until) {
            this.mob = mob;
            this.until = until;
        }
    }

    private FootTackle() {
    }

    public static boolean isDown(Player player) {
        return DOWN_UNTIL.containsKey(player.getUUID());
    }

    private static boolean canBeTackled(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof ServerPlayer player) {
            return !isDown(player);
        }
        return entity instanceof Mob mob && !(mob instanceof FootballEntity) && mob.getBbHeight() <= MOB_MAX_HEIGHT
                && !MOB_DOWN.containsKey(mob.getUUID());
    }

    public static void tackle(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long cooldown = COOLDOWN_UNTIL.get(player.getUUID());
        if (cooldown != null && now < cooldown) {
            return;
        }
        COOLDOWN_UNTIL.put(player.getUUID(), now + COOLDOWN_TICKS);

        Vec3 flat = FootballEntity.flatLook(player);
        LivingEntity target = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(REACH, 1.0, REACH))) {
            if (candidate == player || !canBeTackled(candidate)) {
                continue;
            }
            Vec3 to = candidate.position().subtract(player.position());
            double dist = Math.hypot(to.x, to.z);
            if (dist > REACH || (dist > 0.6 && (to.x * flat.x + to.z * flat.z) / dist < MIN_FACING_DOT)) {
                continue;
            }
            if (dist < best) {
                best = dist;
                target = candidate;
            }
        }
        if (target instanceof ServerPlayer victim) {
            knockDown(victim, flat, now);
        } else if (target instanceof Mob mob) {
            tossMob(mob, flat, now);
        }
    }

    private static void knockDown(ServerPlayer target, Vec3 direction, long now) {
        DOWN_UNTIL.put(target.getUUID(), now + DOWN_TICKS);
        for (FootballEntity ball : target.level().getEntitiesOfClass(FootballEntity.class,
                target.getBoundingBox().inflate(3.0, 1.5, 3.0))) {
            ball.letGo(target);
        }
        setLocked(target, true);
        target.setSprinting(false);
        target.push(direction.x * KNOCK, 0.0, direction.z * KNOCK);
        target.hurtMarked = true;
        FootIdle.broadcast(target, State.TACKLED);
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.9F);
    }

    /** Mob: toss it 2 blocks up; {@link #tick} freezes it once it has landed. */
    private static void tossMob(Mob mob, Vec3 direction, long now) {
        mob.getNavigation().stop();
        mob.setTarget(null);
        mob.setDeltaMovement(direction.x * MOB_TOSS_PUSH, MOB_TOSS_SPEED, direction.z * MOB_TOSS_PUSH);
        mob.hurtMarked = true;
        MOB_DOWN.put(mob.getUUID(), new MobDown(mob, now + DOWN_TICKS));
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.9F);
    }

    private static void freeze(MobDown down) {
        if (!down.mob.isNoAi()) {
            down.mob.addTag(STUN_TAG);
            down.mob.setNoAi(true);
            down.froze = true;
        }
        down.mob.setDeltaMovement(0.0, down.mob.getDeltaMovement().y, 0.0);
        down.landed = true;
        FootIdle.broadcast(down.mob, State.TACKLED);
    }

    private static void endStun(MobDown down) {
        unfreeze(down.mob);
        if (!down.mob.isRemoved()) {
            FootIdle.broadcast(down.mob, State.NONE);
        }
    }

    /** Gives the AI back to a mob this class froze. */
    public static void unfreeze(Mob mob) {
        if (mob.removeTag(STUN_TAG)) {
            mob.setNoAi(false);
        }
    }

    /** Server stop: nobody may stay frozen. */
    public static void releaseAll() {
        MOB_DOWN.values().forEach(FootTackle::endStun);
        MOB_DOWN.clear();
    }

    /** Transient modifiers (never saved): zero walking speed and zero jump strength. */
    private static void setLocked(ServerPlayer player, boolean locked) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (locked) {
            if (speed != null) {
                speed.addOrUpdateTransientModifier(new AttributeModifier(
                        DOWN_SPEED_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            if (jump != null) {
                jump.addOrUpdateTransientModifier(new AttributeModifier(
                        DOWN_JUMP_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        } else {
            if (speed != null) {
                speed.removeModifier(DOWN_SPEED_ID);
            }
            if (jump != null) {
                jump.removeModifier(DOWN_JUMP_ID);
            }
        }
    }

    /** Called every server tick: gets fallen players back up after 4 seconds. */
    public static void tick(MinecraftServer server) {
        if (DOWN_UNTIL.isEmpty() && COOLDOWN_UNTIL.isEmpty() && MOB_DOWN.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        MOB_DOWN.values().removeIf(down -> {
            Mob mob = down.mob;
            if (mob.isRemoved() || !mob.isAlive()) {
                endStun(down);
                return true;
            }
            down.ticks++;
            if (!down.landed && ((down.ticks >= MOB_MIN_AIR_TICKS && (mob.onGround() || mob.isInWater()))
                    || down.ticks > MOB_MAX_AIR_TICKS)) {
                freeze(down);
            }
            if (now >= down.until) {
                endStun(down);
                return true;
            }
            return false;
        });
        COOLDOWN_UNTIL.values().removeIf(until -> until < now);
        DOWN_UNTIL.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                return true;
            }
            if (player.isAlive() && now < entry.getValue()) {
                return false;
            }
            setLocked(player, false);
            if (player.isAlive()) {
                FootIdle.broadcast(player, State.NONE);
            }
            return true;
        });
    }
}
