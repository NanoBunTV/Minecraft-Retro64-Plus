package com.nanobuntv.retro64plus.mixin.client;

import com.nanobuntv.retro64plus.client.CoinHud;
import com.nanobuntv.retro64plus.client.PowerMeterHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SM64's power meter in place of the hearts while you're Mario (D30, D45); the coin counter (D57). 26.1 renamed the
 * HUD's render methods to extract its render state; 26.2 moved them from Gui into Hud.
 */
//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*///?} else {
@Mixin(net.minecraft.client.gui.Gui.class)
//?}
abstract class GuiMixin {
    @Inject(method = {"renderHearts", "extractHearts"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void retro64plus$noHearts(CallbackInfo ci) {
        if (PowerMeterHud.replacesHearts()) ci.cancel();
    }

    @Inject(method = {"render", "extractRenderState"}, at = @At("TAIL"), require = 1)
    private void retro64plus$powerMeter(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        PowerMeterHud.render(graphics, delta.getGameTimeDeltaPartialTick(false));
        CoinHud.render(graphics);
    }
}
