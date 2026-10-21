package com.kintil555.football.entity;

import com.kintil555.football.registry.ModEntities;
import com.kintil555.football.registry.ModItems;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A football. Physics come purely from the same attributes the sulfur cube archetypes use
 * (bounciness, friction modifier, air drag modifier, knockback resistance); it has no AI.
 */
public class FootballEntity extends Mob {
    private static final double BOUNCINESS = 0.75;
    private static final double FRICTION_MODIFIER = 0.3;
    private static final double AIR_DRAG_MODIFIER = 0.05;

    private static final double PUSH_DISTANCE = 1.3;
    private static final double MAX_PUSH_SPEED = 0.5;
    private static final float PUSH_SPEED_SCALE = 0.3F;

    /** Yellowish-white RGB used for the aim highlight outline. */
    public static final int HIGHLIGHT_COLOR = 0xFFE600;

    private static final int TOUCH_CONTROL_TICKS = 30;
    private static final int DRIBBLE_TIMEOUT_TICKS = 4;
    private static final int JUGGLE_RESET_TICKS = 40;
    private static final double DRIBBLE_DISTANCE = 1.15;
    private static final double DRIBBLE_MAX_SPEED = 1.0;
    private static final double DRIBBLE_BREAK_DISTANCE_SQR = 4.5 * 4.5;
    private static final double BALL_COLLISION_DIAMETER = ModEntities.BALL_SIZE;
    private static final double BALL_RESTITUTION = 0.8;

    /** Entity id of the player currently interacting with the ball, or -1. Synced so every client can draw the marker. */
    private static final EntityDataAccessor<Integer> CONTROLLER_ID =
            SynchedEntityData.defineId(FootballEntity.class, EntityDataSerializers.INT);

    /** Entity id of the player holding the ball above their head, or -1. */
    private static final EntityDataAccessor<Integer> HOLDER_ID =
            SynchedEntityData.defineId(FootballEntity.class, EntityDataSerializers.INT);

    private static final int PASS_STEER_TICKS = 40;
    private static final double HEADER_SPEED = 1.1;
    private static final int HEADER_COOLDOWN_TICKS = 8;

    /** Horizontal turn rate (radians per tick) of the velocity; makes kicks curl. Decays over time. */
    private double spin;
    private static final double SPIN_DECAY_AIR = 0.96;
    private static final double SPIN_DECAY_GROUND = 0.85;
    /** Sum of the air decay series: total curl = spin * this. */
    public static final double SPIN_TOTAL_FACTOR = 1.0 / (1.0 - SPIN_DECAY_AIR);

    private long lastKickTick;
    private static final int REGRAB_TICKS = 12;
    private static final int LET_GO_TICKS = 20;
    private static final double AUTO_DRIBBLE_MAX_BALL_SPEED_SQR = 0.35 * 0.35;
    private static final double CATCH_MIN_FALL_SPEED = 0.1;
    private static final long CATCH_MIN_FLIGHT_TICKS = 8;

    private UUID passTargetId;
    private long passUntil;
    private long lastHeaderTick;
    private long controllerUntil;
    private long lastActionTick;
    private UUID dribblerId;
    private long dribbleUntil;
    /** Player who just let go of the ball (shift): cannot auto-dribble or auto-catch it until {@link #noControlUntil}. */
    private UUID noControlId;
    private long noControlUntil;
    private UUID jugglerId;
    private int juggleCount;
    private long lastJuggleTick;
    /** Client only: procedural rolling and squash animation, driven by the ball's momentum. */
    private final BallAnimation animation = new BallAnimation();
    /** Client only: set while the local crosshair is on the ball. */
    private boolean clientHighlighted;

    public FootballEntity(EntityType<? extends FootballEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0)
                .add(Attributes.BOUNCINESS, BOUNCINESS)
                .add(Attributes.FRICTION_MODIFIER, FRICTION_MODIFIER)
                .add(Attributes.AIR_DRAG_MODIFIER, AIR_DRAG_MODIFIER);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(CONTROLLER_ID, -1);
        entityData.define(HOLDER_ID, -1);
    }

    public BallAnimation getAnimation() {
        return this.animation;
    }

    public int getControllerId() {
        return this.entityData.get(CONTROLLER_ID);
    }

    public int getHolderId() {
        return this.entityData.get(HOLDER_ID);
    }

    public boolean isHeld() {
        return this.getHolderId() != -1;
    }

    /** Lifts the ball above {@code player}'s head until it is thrown or passed. */
    public void grab(Player player) {
        this.passTargetId = null;
        this.dribblerId = null;
        this.entityData.set(HOLDER_ID, player.getId());
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.setController(player, 20);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.kintil555.football.action.FootIdle.broadcast(serverPlayer,
                    com.kintil555.football.net.FootAnimPayload.State.HOLD);
        }
    }

    /** Rolls the ball toward {@code spot} (horizontal only); used by the idle foot animation to pull the ball under the foot. */
    public void rollTo(Vec3 spot, double gain) {
        double dx = spot.x - this.getX();
        double dz = spot.z - this.getZ();
        Vec3 v = this.getDeltaMovement();
        if (Math.hypot(dx, dz) < 0.04) {
            this.setDeltaMovement(0.0, v.y, 0.0);
        } else {
            this.setDeltaMovement(Mth.clamp(dx * gain, -0.3, 0.3), v.y, Mth.clamp(dz * gain, -0.3, 0.3));
        }
        this.needsSync = true;
    }

    /** Launches with a curl: {@code omega} rad/tick of horizontal turn, decaying while the ball flies. */
    public void launchCurved(Vec3 velocity, double omega) {
        this.launch(velocity);
        this.spin = omega;
    }

    private void spinStep() {
        if (Math.abs(this.spin) < 1.0E-4) {
            this.spin = 0.0;
            return;
        }
        Vec3 v = this.getDeltaMovement();
        double cos = Math.cos(this.spin);
        double sin = Math.sin(this.spin);
        this.setDeltaMovement(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
        this.needsSync = true;
        this.spin *= this.onGround() ? SPIN_DECAY_GROUND : SPIN_DECAY_AIR;
    }

    /** Shift: drops the held ball or cancels the dribble, and stops {@code player} from instantly regaining it. */
    public boolean letGo(Player player) {
        boolean held = this.getHolderId() == player.getId();
        boolean dribbling = player.getUUID().equals(this.dribblerId);
        if (!held && !dribbling) {
            return false;
        }
        long now = this.level().getGameTime();
        this.noControlId = player.getUUID();
        this.noControlUntil = now + LET_GO_TICKS;
        this.dribblerId = null;
        if (held) {
            this.release();
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.kintil555.football.action.FootIdle.broadcast(serverPlayer,
                        com.kintil555.football.net.FootAnimPayload.State.NONE);
            }
            Vec3 flat = flatLook(player);
            this.setDeltaMovement(flat.x * 0.08, 0.0, flat.z * 0.08);
        } else {
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(v.x * 0.3, v.y, v.z * 0.3);
        }
        this.needsSync = true;
        return true;
    }

    private boolean cannotControl(Player player, long now) {
        return now < this.noControlUntil && player.getUUID().equals(this.noControlId);
    }

    public void release() {
        this.entityData.set(HOLDER_ID, -1);
        this.setNoGravity(false);
    }

    /** After a pass the ball steers gently toward {@code target} for a short time so it reliably arrives. */
    public void setPassTarget(Player target) {
        this.passTargetId = target.getUUID();
        this.passUntil = this.level().getGameTime() + PASS_STEER_TICKS;
    }

    public void setClientHighlighted(boolean highlighted) {
        this.clientHighlighted = highlighted;
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return super.isCurrentlyGlowing() || (this.level().isClientSide() && this.clientHighlighted);
    }

    @Override
    public int getTeamColor() {
        return this.clientHighlighted ? HIGHLIGHT_COLOR : super.getTeamColor();
    }

    /** Marks {@code player} as interacting with the ball for the next {@code ticks} ticks (server only). */
    public void setController(Player player, int ticks) {
        if (this.level().isClientSide()) {
            return;
        }
        this.controllerUntil = Math.max(this.controllerUntil, this.level().getGameTime() + ticks);
        if (this.getControllerId() != player.getId()) {
            this.entityData.set(CONTROLLER_ID, player.getId());
        }
    }

    /** Returns true (and starts the cooldown) when enough time passed since the last discrete action. */
    public boolean tryBeginAction(int cooldownTicks) {
        long now = this.level().getGameTime();
        if (now - this.lastActionTick < cooldownTicks) {
            return false;
        }
        this.lastActionTick = now;
        return true;
    }

    /** Replaces the ball velocity and syncs it to clients. */
    public void launch(Vec3 velocity) {
        this.spin = 0.0;
        this.dribblerId = null;
        this.lastKickTick = this.level().getGameTime();
        this.setDeltaMovement(velocity);
        this.needsSync = true;
    }

    public void startDribble(Player player) {
        if (this.isDribbledByOther(player, this.level().getGameTime())) {
            return;
        }
        this.dribblerId = player.getUUID();
        this.dribbleUntil = this.level().getGameTime() + DRIBBLE_TIMEOUT_TICKS;
        this.setController(player, DRIBBLE_TIMEOUT_TICKS + 6);
    }

    /** Registers one juggle touch and returns how many consecutive juggles the player has. */
    public int registerJuggle(Player player) {
        long now = this.level().getGameTime();
        if (!player.getUUID().equals(this.jugglerId) || now - this.lastJuggleTick > JUGGLE_RESET_TICKS) {
            this.juggleCount = 0;
        }
        this.jugglerId = player.getUUID();
        this.lastJuggleTick = now;
        return ++this.juggleCount;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.animation.tick(this);
            return;
        }
        long now = this.level().getGameTime();
        if (this.isHeld()) {
            Entity holder = this.level().getEntity(this.getHolderId());
            if (!(holder instanceof Player player) || player.isRemoved() || player.isSpectator()
                    || this.distanceToSqr(player) > 64.0) {
                this.release();
            } else {
                this.holdStep(player, now);
                return;
            }
        }
        this.spinStep();
        this.collideWithBalls();
        this.autoCatch(now);
        if (this.isHeld()) {
            return;
        }
        this.passStep(now);
        this.headerStep(now);
        this.autoDribble(now);
        if (this.dribblerId != null) {
            Player dribbler = this.level().getPlayerByUUID(this.dribblerId);
            if (dribbler == null || now > this.dribbleUntil || this.distanceToSqr(dribbler) > DRIBBLE_BREAK_DISTANCE_SQR) {
                this.dribblerId = null;
            } else {
                this.dribbleStep(dribbler);
            }
        }
        if (this.getControllerId() != -1 && now > this.controllerUntil) {
            this.entityData.set(CONTROLLER_ID, -1);
        }
    }

    /** Floats the ball just above the holder's head. */
    private void holdStep(Player player, long now) {
        Vec3 flat = flatLook(player);
        this.setPos(player.getX() + flat.x * 0.15, player.getY() + player.getBbHeight() + 0.2, player.getZ() + flat.z * 0.15);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0;
        this.needsSync = true;
        this.setController(player, 10);
    }

    private void passStep(long now) {
        if (this.passTargetId == null) {
            return;
        }
        Player target = this.level().getPlayerByUUID(this.passTargetId);
        if (target == null || now > this.passUntil || this.onGround() && now > this.passUntil - PASS_STEER_TICKS + 5) {
            this.passTargetId = null;
            return;
        }
        Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0).subtract(this.position());
        double dist = Math.sqrt(to.x * to.x + to.z * to.z);
        if (dist < 1.2) {
            this.passTargetId = null;
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        double speed = Math.max(0.35, Math.sqrt(motion.x * motion.x + motion.z * motion.z));
        double vx = Mth.lerp(0.18, motion.x, to.x / dist * speed);
        double vz = Mth.lerp(0.18, motion.z, to.z / dist * speed);
        this.launch(new Vec3(vx, motion.y, vz));
    }

    /** A player who is in the air and meets the ball with their head heads it in their look direction. */
    private void headerStep(long now) {
        if (now - this.lastHeaderTick < HEADER_COOLDOWN_TICKS) {
            return;
        }
        for (Player player : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(0.6))) {
            if (player.isSpectator() || player.onGround()) {
                continue;
            }
            double headBottom = player.getY() + player.getBbHeight() - 0.7;
            double headTop = player.getY() + player.getBbHeight() + 0.35;
            if (this.getY() + this.getBbHeight() < headBottom || this.getY() > headTop) {
                continue;
            }
            Vec3 look = player.getLookAngle();
            Vec3 flat = flatLook(player);
            this.lastHeaderTick = now;
            this.passTargetId = null;
            this.launch(new Vec3(flat.x * HEADER_SPEED, Mth.clamp(0.2 - look.y * 0.5, 0.05, 0.7), flat.z * HEADER_SPEED));
            this.setController(player, 50);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.SLIME_BLOCK_HIT, net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 1.4F);
            return;
        }
    }

    /**
     * Walking into a slow, grounded ball starts a dribble automatically: the ball stays in front of the player
     * and follows their movement until they stop, kick or lose it.
     */
    private void autoDribble(long now) {
        if (now - this.lastKickTick < REGRAB_TICKS || !this.onGround()) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        boolean slow = motion.x * motion.x + motion.z * motion.z < AUTO_DRIBBLE_MAX_BALL_SPEED_SQR;
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player player : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(1.2, 0.8, 1.2))) {
            Vec3 pm = player.getKnownMovement();
            double dy = this.getY() - player.getY();
            boolean moving = pm.x * pm.x + pm.z * pm.z > 0.0009;
            boolean owner = player.getUUID().equals(this.dribblerId);
            if (player.isSpectator() || this.cannotControl(player, now) || !moving || dy < -0.8 || dy > 1.0 || !(slow || owner)
                    || !owner && this.isDribbledByOther(player, now)) {
                continue;
            }
            double d = this.distanceToSqr(player);
            if (d < bestDist) {
                bestDist = d;
                best = player;
            }
        }
        if (best != null) {
            this.dribblerId = best.getUUID();
            this.dribbleUntil = now + DRIBBLE_TIMEOUT_TICKS;
            this.setController(best, DRIBBLE_TIMEOUT_TICKS + 6);
        }
    }

    /** True when {@code player} is already dribbling a different ball: a player can only dribble one at a time. */
    private boolean isDribbledByOther(Player player, long now) {
        for (FootballEntity other : this.level().getEntitiesOfClass(FootballEntity.class, player.getBoundingBox().inflate(5.0))) {
            if (other != this && player.getUUID().equals(other.dribblerId) && now <= other.dribbleUntil) {
                return true;
            }
        }
        return false;
    }

    /** Footballs collide with each other like equal-mass spheres: they separate and exchange momentum. */
    private void collideWithBalls() {
        for (FootballEntity other : this.level().getEntitiesOfClass(FootballEntity.class, this.getBoundingBox().inflate(0.05))) {
            if (other == this || other.isHeld() || other.getId() < this.getId()) {
                continue;
            }
            Vec3 delta = other.position().subtract(this.position());
            if (this.onGround() && other.onGround()) {
                delta = delta.multiply(1.0, 0.0, 1.0);
            }
            double distSqr = delta.lengthSqr();
            if (distSqr >= BALL_COLLISION_DIAMETER * BALL_COLLISION_DIAMETER) {
                continue;
            }
            double dist = Math.sqrt(distSqr);
            Vec3 normal = dist > 1.0E-4 ? delta.scale(1.0 / dist) : new Vec3(1.0, 0.0, 0.0);
            Vec3 push = normal.scale((BALL_COLLISION_DIAMETER - dist) * 0.5);
            this.setPos(this.position().subtract(push));
            other.setPos(other.position().add(push));
            Vec3 va = this.getDeltaMovement();
            Vec3 vb = other.getDeltaMovement();
            double approach = va.subtract(vb).dot(normal);
            if (approach > 0.0) {
                Vec3 impulse = normal.scale(approach * (1.0 + BALL_RESTITUTION) * 0.5);
                this.setDeltaMovement(va.subtract(impulse));
                other.setDeltaMovement(vb.add(impulse));
            }
            this.needsSync = true;
            other.needsSync = true;
        }
    }

    /** A player standing still under a falling ball (goalkeeper style) catches it above the head. */
    private void autoCatch(long now) {
        Vec3 v = this.getDeltaMovement();
        if (this.isHeld() || this.onGround() || v.y > -CATCH_MIN_FALL_SPEED || v.lengthSqr() < 0.04
                || now - this.lastKickTick < CATCH_MIN_FLIGHT_TICKS) {
            return;
        }
        for (Player player : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(0.5, 2.0, 0.5))) {
            Vec3 pm = player.getKnownMovement();
            double rel = this.getY() - player.getY();
            double dx = this.getX() - player.getX();
            double dz = this.getZ() - player.getZ();
            if (player.isSpectator() || this.cannotControl(player, now) || pm.x * pm.x + pm.z * pm.z > 0.002
                    || rel < 0.5 || rel > player.getBbHeight() + 0.5 || dx * dx + dz * dz > 1.0) {
                continue;
            }
            this.grab(player);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.football.catch"));
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    net.minecraft.sounds.SoundEvents.SLIME_BLOCK_HIT, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.8F);
            return;
        }
    }

    /** The ball never shoves the player around: dribbling must not slow the player down. */
    @Override
    protected void doPush(Entity entity) {
    }

    /** Keeps the ball glued in front of the dribbler's feet, matching their running speed. */
    private void dribbleStep(Player player) {
        Vec3 movement = player.getKnownMovement().multiply(1.0, 0.0, 1.0);
        Vec3 heading = movement.lengthSqr() > 0.0004 ? movement.normalize() : flatLook(player);
        double targetX = player.getX() + heading.x * DRIBBLE_DISTANCE;
        double targetZ = player.getZ() + heading.z * DRIBBLE_DISTANCE;
        double vx = movement.x + (targetX - this.getX()) * 0.35;
        double vz = movement.z + (targetZ - this.getZ()) * 0.35;
        double speed = Math.sqrt(vx * vx + vz * vz);
        if (speed > DRIBBLE_MAX_SPEED) {
            vx *= DRIBBLE_MAX_SPEED / speed;
            vz *= DRIBBLE_MAX_SPEED / speed;
        }
        this.setDeltaMovement(new Vec3(vx, this.getDeltaMovement().y, vz));
        this.needsSync = true;
    }

    public static Vec3 flatLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        return flat.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    /** The ball takes no damage (except void/kill); hits only knock it away. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, damage);
        }
        if (source.getEntity() != null && !source.is(DamageTypeTags.NO_KNOCKBACK)) {
            this.dealDefaultKnockback(source, damage, true);
        }
        if (source.getEntity() instanceof Player attacker) {
            this.setController(attacker, TOUCH_CONTROL_TICKS);
        }
        return true;
    }

    /** Shift + empty-hand right click picks the ball back up. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive() || !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            ItemStack stack = new ItemStack(ModItems.FOOTBALL);
            if (!player.getInventory().add(stack)) {
                this.spawnAtLocation(serverLevel, stack);
            }
            this.discard();
        }
        return InteractionResult.SUCCESS;
    }

    /** Walking into the ball rolls it away, same impulse formula as the sulfur cube. */
    @Override
    public void playerTouch(Player player) {
        super.playerTouch(player);
        if (this.isHeld()) {
            return;
        }
        Vec3 offset = this.position().subtract(player.position());
        double ballBottom = this.getY();
        double ballTop = ballBottom + this.getBbHeight();
        double playerBottom = player.getY();
        double playerTop = playerBottom + player.getBbHeight();
        if (offset.horizontalDistance() >= PUSH_DISTANCE || playerBottom > ballTop || playerTop <= ballBottom) {
            return;
        }

        double knockback = Math.max(0.0, 1.0 - this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        Vec3 direction = offset.multiply(1.0, 0.0, 1.0).normalize().scale(knockback);
        double speed = Mth.clamp(player.getKnownSpeed().length() * 2.0 * PUSH_SPEED_SCALE, 0.0, MAX_PUSH_SPEED);
        Vec3 impulse = new Vec3(direction.x, this.onGround() ? knockback * 0.3 : 0.0, direction.z).scale(speed);
        this.spin = 0.0;
        this.needsSync = true;
        this.addDeltaMovement(impulse);
        this.setController(player, TOUCH_CONTROL_TICKS);
    }
}
