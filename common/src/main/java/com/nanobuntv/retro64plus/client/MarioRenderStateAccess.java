package com.nanobuntv.retro64plus.client;

import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * From 1.21.9 a player's render state carries the player it was extracted from (mixin/client/AvatarRenderStateMixin,
 * filled by PlayerRendererMixin), so the submit step can draw Mario instead (LivingEntityRendererMixin).
 */
public interface MarioRenderStateAccess {
    @Nullable AbstractClientPlayer retro64plus$player();

    float retro64plus$partialTick();

    /** Whether the inventory's paper doll extracted it. */
    boolean retro64plus$inInventory();

    void retro64plus$set(@Nullable AbstractClientPlayer player, float partialTick, boolean inInventory);
}
