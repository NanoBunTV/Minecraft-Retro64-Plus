package com.nanobuntv.retro64plus.mixin;

import com.nanobuntv.retro64plus.server.ServerCoins;
import com.nanobuntv.retro64plus.server.ServerStars;
import java.util.UUID;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Ender Dragon never goes through {@code die} or its loot: it plays its own death, which takes longer than vanilla
 * keeps the kill credit. The last player to hurt it is remembered here; on the first tick of the death, their coins
 * (D52) and star (D61).
 */
@Mixin(EnderDragon.class)
abstract class EnderDragonMixin {
    @Shadow
    public int dragonDeathTime;

    @Unique
    private UUID retro64plus$lastPlayer;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void retro64plus$rememberAttacker(CallbackInfo ci) {
        if (((EnderDragon) (Object) this).getKillCredit() instanceof Player player) {
            retro64plus$lastPlayer = player.getUUID();
        }
    }

    @Inject(method = "tickDeath", at = @At("HEAD"))
    private void retro64plus$bossRewards(CallbackInfo ci) {
        EnderDragon dragon = (EnderDragon) (Object) this;
        if (dragonDeathTime != 0 || dragon.level().isClientSide() || retro64plus$lastPlayer == null) return;
        Player killer = dragon.level().getPlayerByUUID(retro64plus$lastPlayer);
        if (killer == null) return;
        ServerCoins.onMobDeath(dragon, dragon.damageSources().generic(), killer);
        ServerStars.onBossDeath(dragon, killer);
    }
}
