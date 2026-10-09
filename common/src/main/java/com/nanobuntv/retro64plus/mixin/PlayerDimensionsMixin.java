package com.nanobuntv.retro64plus.mixin;

import com.nanobuntv.retro64plus.MarioHooks;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mario's hitbox (0.5 × 1.3), except asleep or dying, where vanilla's poses are needed. From 1.21.9 the dimensions
 * belong to Avatar (players and mannequins), so only players are changed.
 */
//? if >=1.21.9 {
/*@Mixin(net.minecraft.world.entity.Avatar.class)
*///?} else {
@Mixin(Player.class)
//?}
abstract class PlayerDimensionsMixin {
    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void retro64plus$marioSize(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (pose != Pose.SLEEPING && pose != Pose.DYING && (Object) this instanceof Player player
                && MarioHooks.isMario(player)) {
            cir.setReturnValue(MarioHooks.MARIO_DIMENSIONS);
        }
    }
}
