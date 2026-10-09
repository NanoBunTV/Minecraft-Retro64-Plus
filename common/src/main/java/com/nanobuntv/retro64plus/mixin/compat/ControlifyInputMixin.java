package com.nanobuntv.retro64plus.mixin.compat;

import com.nanobuntv.retro64plus.client.LocalMario;
import dev.isxander.controlify.controller.input.ControllerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * D31: Controlify (isXander, 3.x) sees every controller at rest while you're Mario: each driver hands its state through
 * {@code InputComponent.pushState}, so a neutral state there releases what Controlify was holding and stops its
 * movement, look, virtual mouse and menus, without switching modes. Real input resumes when Mario turns off. Applies
 * only when Controlify is installed (@Pseudo, optional config).
 */
@Pseudo
@Mixin(targets = "dev.isxander.controlify.controller.input.InputComponent", remap = false)
public abstract class ControlifyInputMixin {
    @ModifyVariable(method = "pushState", at = @At("HEAD"), argsOnly = true, remap = false)
    private ControllerState retro64plus$restWhileMario(ControllerState state) {
        return LocalMario.isActive() ? ControllerState.EMPTY : state;
    }
}
