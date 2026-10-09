package com.nanobuntv.retro64plus.mixin;

import com.nanobuntv.retro64plus.MarioHooks;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** How being Mario changes a player, on both sides (MarioHooks says who is Mario on each). */
@Mixin(Player.class)
abstract class PlayerMixin {
    /** While the local player is Mario, libsm64 moves them: vanilla walking, falling and swimming are skipped. */
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void retro64plus$skipTravel(Vec3 input, CallbackInfo ci) {
        if (MarioHooks.isLocalMario((Player) (Object) this)) ci.cancel();
    }

    /** Mario's own voice says it (D36): no vanilla hurt sound. */
    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void retro64plus$noHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        if (MarioHooks.isMario((Player) (Object) this)) cir.setReturnValue(null);
    }

    /** SM64's death action has its own sound (D37). */
    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void retro64plus$noDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
        if (MarioHooks.isMario((Player) (Object) this)) cir.setReturnValue(null);
    }

    /**
     * SM64 handles falls and reports their damage itself (D36, server/ServerMarioHealth); no vanilla fall damage. A
     * mount's fall still reaches its rider (a pig or strider passes it on through here): SM64 doesn't move him then.
     */
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    //? if >=1.21.5 {
    /*private void retro64plus$noFallDamage(double distance, float multiplier, DamageSource source,
            CallbackInfoReturnable<Boolean> cir) {
    *///?} else {
    private void retro64plus$noFallDamage(float distance, float multiplier, DamageSource source,
            CallbackInfoReturnable<Boolean> cir) {
    //?}
        Player player = (Player) (Object) this;
        if (MarioHooks.isMario(player) && !player.isPassenger()) cir.setReturnValue(false);
    }
}
