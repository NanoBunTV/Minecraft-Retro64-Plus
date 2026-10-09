package com.nanobuntv.retro64plus.mixin.compat;

import com.nanobuntv.retro64plus.client.LocalMario;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * D31: Controllable (MrCrayfish, 1.21.1 0.25.x) sees no controller while you're Mario, so it doesn't move, turn or press
 * anything alongside Retro64 Plus's own controller input. All of its input, cursor and menus check
 * {@code Controllable.getController()} for null. Applies only when Controllable is installed (@Pseudo, optional config).
 */
@Pseudo
@Mixin(targets = "com.mrcrayfish.controllable.Controllable", remap = false)
public abstract class ControllableMixin {
    @Inject(method = "getController", at = @At("HEAD"), cancellable = true, remap = false)
    private static void retro64plus$noControllerWhileMario(CallbackInfoReturnable<Object> cir) {
        if (LocalMario.isActive()) cir.setReturnValue(null);
    }
}
