package com.kintil555.football.mixin;

import com.kintil555.football.client.FootAnimClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client only: render states do not know their entity, so tackled mobs are remembered here (see LivingEntityRendererMixin). */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("TAIL"))
    private void football$noteEntity(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        FootAnimClient.noteRenderState(state, entity);
    }
}
