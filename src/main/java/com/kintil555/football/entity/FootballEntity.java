package com.kintil555.football.entity;

import com.kintil555.football.registry.ModItems;
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
    }
}
