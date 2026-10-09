package com.nanobuntv.retro64plus.mixin;

import com.nanobuntv.retro64plus.caps.MarioCap;
import com.nanobuntv.retro64plus.server.ServerCaps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Warden.class)
abstract class WardenMixin {
    /**
     * The Warden picks targets without asking {@code canBeSeenAsEnemy} (mixin/LivingEntityMixin), so the Vanish cap
     * (D43) needs this too. His vibrations still hear Mario: sculk sensors and shriekers react as usual.
     */
    @Inject(method = "canTargetEntity", at = @At("HEAD"), cancellable = true)
    private void retro64plus$vanishCap(@Nullable Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ServerPlayer player && ServerCaps.has(player, MarioCap.VANISH)) {
            cir.setReturnValue(false);
        }
    }
}
