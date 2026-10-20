package com.kintil555.football.mixin;

import com.kintil555.football.client.FootballInput;
import com.kintil555.football.entity.FootballEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Client only: colors the pass-target outline yellowish-white. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void football$passColor(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this == FootballInput.passTarget()) {
            cir.setReturnValue(FootballEntity.HIGHLIGHT_COLOR);
        }
    }
}
