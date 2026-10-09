package com.nanobuntv.retro64plus.mixin.client;

import com.nanobuntv.retro64plus.MarioHooks;
import com.nanobuntv.retro64plus.client.LocalMario;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    /** The server says the local player was hurt: SM64's knockback and voice follow (D36). */
    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void retro64plus$marioHurt(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof Player player && MarioHooks.isLocalMario(player)) LocalMario.onDamaged(source);
    }
}
