package com.kintil555.football.mixin;

import com.kintil555.football.client.FootAnimClient;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client only: layers the football body animations (hold / throw / foot on ball) over the vanilla player pose. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void football$ballPose(AvatarRenderState state, CallbackInfo ci) {
        FootAnimClient.apply((PlayerModel) (Object) this, state);
    }
}
