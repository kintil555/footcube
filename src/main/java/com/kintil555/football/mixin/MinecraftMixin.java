package com.kintil555.football.mixin;

import com.kintil555.football.client.FootballInput;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client only: makes the aimed pass target glow, visible to the thrower alone. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void football$passGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity == FootballInput.passTarget()) {
            cir.setReturnValue(true);
        }
    }
}
