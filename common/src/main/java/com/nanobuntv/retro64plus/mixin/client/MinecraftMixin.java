package com.nanobuntv.retro64plus.mixin.client;

import com.nanobuntv.retro64plus.client.LocalMario;
import com.nanobuntv.retro64plus.client.RomSetupScreen;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Gui.class)
*///?} else {
@Mixin(Minecraft.class)
//?}
abstract class MinecraftMixin {
    /**
     * Dying as Mario: the death screen waits for SM64's death animation (D37). It comes both from the kill packet and
     * from {@code setScreen(null)} while the player is dead (which builds one itself), so both are held.
     */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void retro64plus$holdDeathScreen(@Nullable Screen screen, CallbackInfo ci) {
        if ((screen == null || screen instanceof DeathScreen) && LocalMario.holdDeathScreen(screen)) ci.cancel();
    }

    /**
     * The ROM gate (D81, D82): last of the screens shown before the title screen or a quick play, so after the
     * accessibility screen. The game goes on only once the ROM is found and loaded.
     */
    @Inject(method = "addInitialScreens", at = @At("TAIL"))
    //? if >=1.21.9 {
    /*private void retro64plus$romGate(List<Function<Runnable, Screen>> output,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
    *///?} else {
    private void retro64plus$romGate(List<Function<Runnable, Screen>> output, CallbackInfo ci) {
    //?}
        output.add(RomSetupScreen::new);
    }
}
