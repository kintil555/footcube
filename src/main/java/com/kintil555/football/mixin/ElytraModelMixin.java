package com.kintil555.football.mixin;

import com.kintil555.football.client.FootAnimClient;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client only: the elytra is not a humanoid model, so it gets the animated body's lean / offset here. */
@Mixin(ElytraModel.class)
public abstract class ElytraModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void football$bodyPose(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar) {
            FootAnimClient.applyBodyTo(((EntityModel<?>) (Object) this).root(), avatar);
        }
    }
}
