package com.kintil555.football.mixin;

import com.kintil555.football.client.FootAnimClient;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client only: while the local player is tackled, the first-person camera falls and tilts back with the body. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    private static final double MIN_EYE_ABOVE_FEET = 0.2;
    private static final Vector3f FORWARDS = new Vector3f(0.0F, 0.0F, -1.0F);
    private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Vector3f LEFT = new Vector3f(-1.0F, 0.0F, 0.0F);

    @Shadow
    private Entity entity;
    @Shadow
    private Vec3 position;
    @Shadow
    private boolean detached;
    @Shadow
    @Final
    private Quaternionf rotation;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;
    @Shadow
    private int matrixPropertiesDirty;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity(F)V", at = @At("TAIL"))
    private void football$tackleCamera(float partialTicks, CallbackInfo ci) {
        if (this.detached || this.entity == null) {
            return;
        }
        Vector3f delta = new Vector3f();
        Quaternionf world = new Quaternionf();
        if (!FootAnimClient.fallCamera(this.entity, partialTicks, delta, world)) {
            return;
        }
        double feetY = Mth.lerp(partialTicks, this.entity.yo, this.entity.getY());
        double y = Math.max(this.position.y + delta.y, feetY + MIN_EYE_ABOVE_FEET);
        this.setPosition(new Vec3(this.position.x + delta.x, y, this.position.z + delta.z));
        this.rotation.set(new Quaternionf(world).mul(this.rotation));
        FORWARDS.rotate(this.rotation, this.forwards);
        UP.rotate(this.rotation, this.up);
        LEFT.rotate(this.rotation, this.left);
        this.matrixPropertiesDirty |= 3;
    }
}
