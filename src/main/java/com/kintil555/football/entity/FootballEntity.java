package com.kintil555.football.entity;

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
    public static final int HIGHLIGHT_COLOR = 0xFFF4C4;

    private static final int TOUCH_CONTROL_TICKS = 30;
    private static final int DRIBBLE_TIMEOUT_TICKS = 4;
    private static final int JUGGLE_RESET_TICKS = 40;
    private static final double DRIBBLE_DISTANCE = 0.9;
    private static final double DRIBBLE_MAX_SPEED = 0.6;
    private static final double DRIBBLE_BREAK_DISTANCE_SQR = 3.5 * 3.5;

    /** Entity id of the player currently interacting with the ball, or -1. Synced so every client can draw the marker. */
    private static final EntityDataAccessor<Integer> CONTROLLER_ID =
            SynchedEntityData.defineId(FootballEntity.class, EntityDataSerializers.INT);

    private long controllerUntil;
    private long lastActionTick;
    private UUID dribblerId;
    private long dribbleUntil;
    private UUID jugglerId;
    private int juggleCount;
    private long lastJuggleTick;
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
    }

    public int getControllerId() {
        return this.entityData.get(CONTROLLER_ID);
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
        this.setDeltaMovement(velocity);
        this.needsSync = true;
    }

    public void startDribble(Player player) {
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
            return;
        }
        long now = this.level().getGameTime();
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
        this.launch(new Vec3(vx, this.getDeltaMovement().y, vz));
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
        this.needsSync = true;
        this.addDeltaMovement(impulse);
        this.setController(player, TOUCH_CONTROL_TICKS);
    }
}
