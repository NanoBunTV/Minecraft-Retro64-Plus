package com.nanobuntv.retro64plus.mixin.client;

import com.nanobuntv.retro64plus.client.ControllerInput;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every rendered frame, after the mouse turned the camera: the gamepad's right stick turns it too (D31). */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
    private void retro64plus$controllerLook(CallbackInfo ci) {
        ControllerInput.turnCamera();
    }
}
