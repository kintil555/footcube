package com.kintil555.football.mixin;

import com.kintil555.football.client.FootAnimClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client only: layers the football body animations over the vanilla pose of every humanoid model drawn for a player,
 * i.e. the player model and the armor models, so armor follows the animated arms, legs and head.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void football$ballPose(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar) {
            FootAnimClient.apply((HumanoidModel<?>) (Object) this, avatar);
        }
    }
}
