package com.nanobuntv.retro64plus.mixin.client;

//? if >=1.21.9 {
/*import com.nanobuntv.retro64plus.client.MarioRenderStateAccess;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/^* From 1.21.9: a player's render state remembers the player, for drawing Mario at submit time (client/MarioRenderStateAccess). ^/
@Mixin(AvatarRenderState.class)
abstract class AvatarRenderStateMixin implements MarioRenderStateAccess {
    @Unique
    private @Nullable AbstractClientPlayer retro64plus$player;
    @Unique
    private float retro64plus$partialTick;
    @Unique
    private boolean retro64plus$inInventory;

    @Override
    public @Nullable AbstractClientPlayer retro64plus$player() {
        return retro64plus$player;
    }

    @Override
    public float retro64plus$partialTick() {
        return retro64plus$partialTick;
    }

    @Override
    public boolean retro64plus$inInventory() {
        return retro64plus$inInventory;
    }

    @Override
    public void retro64plus$set(@Nullable AbstractClientPlayer player, float partialTick, boolean inInventory) {
        retro64plus$player = player;
        retro64plus$partialTick = partialTick;
        retro64plus$inInventory = inInventory;
    }
}
*///?}
