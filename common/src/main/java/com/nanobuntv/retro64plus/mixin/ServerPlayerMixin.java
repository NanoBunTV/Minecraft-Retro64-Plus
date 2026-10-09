package com.nanobuntv.retro64plus.mixin;

import com.nanobuntv.retro64plus.server.ServerCaps;
import com.nanobuntv.retro64plus.server.ServerCoins;
import com.nanobuntv.retro64plus.server.ServerStars;
import com.nanobuntv.retro64plus.server.ServerMarioHealth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Server players' per-tick Mario rules (no loader event needed). */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    /** SM64's healing at the water surface (D38); caps running out (D17). */
    @Inject(method = "tick", at = @At("TAIL"))
    private void retro64plus$marioHealth(CallbackInfo ci) {
        com.nanobuntv.retro64plus.server.ServerMarios.checkRules(
                com.nanobuntv.retro64plus.util.Compat.server((ServerPlayer) (Object) this));
        ServerMarioHealth.tick((ServerPlayer) (Object) this);
        ServerCaps.tick((ServerPlayer) (Object) this);
        ServerCoins.tick((ServerPlayer) (Object) this);
    }

    /** Metal Mario's immunity (D42). */
    //? if >=1.21.2 {
    /*@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void retro64plus$metalCap(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        if (ServerCaps.blocksDamage((ServerPlayer) (Object) this, source)) cir.setReturnValue(false);
    }
    *///?} else {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void retro64plus$metalCap(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (ServerCaps.blocksDamage((ServerPlayer) (Object) this, source)) cir.setReturnValue(false);
    }
    //?}

    /**
     * Caps come off when Mario dies; half the coins scatter (D56). At die's first vanilla call rather than its start:
     * NeoForge's death event, which a mod may cancel (the player lives), runs before it.
     */
    @Inject(method = "die", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;gameEvent(Lnet/minecraft/core/Holder;)V", ordinal = 0))
    private void retro64plus$capsOff(DamageSource source, CallbackInfo ci) {
        ServerCaps.clear((ServerPlayer) (Object) this);
        ServerCoins.onDeath((ServerPlayer) (Object) this);
        ServerStars.onDeath((ServerPlayer) (Object) this);
    }
}
