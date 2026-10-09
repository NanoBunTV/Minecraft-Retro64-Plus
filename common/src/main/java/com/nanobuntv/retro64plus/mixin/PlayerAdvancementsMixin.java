package com.nanobuntv.retro64plus.mixin;

import com.nanobuntv.retro64plus.server.ServerStars;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Power Star for every challenge advancement a player completes (D65), when its rewards are given. */
@Mixin(PlayerAdvancements.class)
abstract class PlayerAdvancementsMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private void retro64plus$challengeStar(AdvancementHolder advancement, String criterion,
            CallbackInfoReturnable<Boolean> cir) {
        ServerStars.onAdvancement(player, advancement);
    }
}
