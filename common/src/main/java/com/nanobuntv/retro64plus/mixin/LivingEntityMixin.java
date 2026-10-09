package com.nanobuntv.retro64plus.mixin;

import com.nanobuntv.retro64plus.caps.MarioCap;
import com.nanobuntv.retro64plus.server.ServerCaps;
import com.nanobuntv.retro64plus.server.ServerCoins;
import com.nanobuntv.retro64plus.server.ServerStars;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    //? if <1.21.5 {
    @Shadow
    protected Player lastHurtByPlayer;
    @Shadow
    protected int lastHurtByPlayerTime;
    //?}

    /**
     * Mobs ignore a Mario wearing the Vanish cap (D43): their targeting asks this, and they give up a target that
     * stops being one.
     */
    @Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
    private void retro64plus$vanishCap(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player && ServerCaps.has(player, MarioCap.VANISH)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Coins from the mobs Mario defeats (D52), with the rest of the death loot; a boss's star for its killer (D61).
     * The Ender Dragon dies its own way (mixin/EnderDragonMixin).
     */
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void retro64plus$coins(ServerLevel level, DamageSource source, CallbackInfo ci) {
        //? if >=1.21.5 {
        /*Player lastPlayer = ((LivingEntity) (Object) this).getLastHurtByPlayer();
        *///?} else {
        Player lastPlayer = lastHurtByPlayerTime > 0 ? lastHurtByPlayer : null;
        //?}
        ServerCoins.onMobDeath((LivingEntity) (Object) this, source, lastPlayer);
        ServerStars.onBossDeath((LivingEntity) (Object) this,
                source.getEntity() instanceof Player killer ? killer : lastPlayer);
    }
}
