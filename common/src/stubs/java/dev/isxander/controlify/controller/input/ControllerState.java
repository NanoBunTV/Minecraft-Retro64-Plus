package dev.isxander.controlify.controller.input;

/**
 * Compile-only stand-in for Controlify's class (D31), for the Minecraft versions with no Controlify build to compile
 * against (multiloader-common). Never shipped: the real Controlify supplies it at runtime, and without Controlify the
 * compat mixin doesn't apply.
 */
public interface ControllerState {
    ControllerState EMPTY = null;
}
