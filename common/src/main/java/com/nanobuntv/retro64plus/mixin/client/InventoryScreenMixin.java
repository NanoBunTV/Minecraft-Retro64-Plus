package com.nanobuntv.retro64plus.mixin.client;

import com.nanobuntv.retro64plus.client.MarioRenderer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.21.9 {
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
//?}

/**
 * Tells MarioRenderer when the inventory draws the player, so Mario is drawn there around his own position (upstream
 * guessed this from a light value of 15728880 and a partial tick of exactly 1). From 1.21.9 the inventory only
 * extracts the player's render state here (drawn later), so the flag goes into that state (PlayerRendererMixin).
 */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenMixin {
    //? if >=1.21.9 {
    /*@Inject(method = {"renderEntityInInventoryFollowsMouse", "extractEntityInInventoryFollowsMouse"}, at = @At("HEAD"),
            require = 1)
    private static void retro64plus$start(CallbackInfo ci) {
        MarioRenderer.setInInventory(true);
    }

    @Inject(method = {"renderEntityInInventoryFollowsMouse", "extractEntityInInventoryFollowsMouse"}, at = @At("RETURN"),
            require = 1)
    private static void retro64plus$end(CallbackInfo ci) {
        MarioRenderer.setInInventory(false);
    }
    *///?} else {
    @Inject(method = "renderEntityInInventory", at = @At("HEAD"))
    private static void retro64plus$start(GuiGraphics graphics, float x, float y, float scale, Vector3f translate,
            Quaternionf pose, Quaternionf cameraOrientation, LivingEntity entity, CallbackInfo ci) {
        MarioRenderer.setInInventory(true);
    }

    @Inject(method = "renderEntityInInventory", at = @At("RETURN"))
    private static void retro64plus$end(GuiGraphics graphics, float x, float y, float scale, Vector3f translate,
            Quaternionf pose, Quaternionf cameraOrientation, LivingEntity entity, CallbackInfo ci) {
        MarioRenderer.setInInventory(false);
    }
    //?}
}
