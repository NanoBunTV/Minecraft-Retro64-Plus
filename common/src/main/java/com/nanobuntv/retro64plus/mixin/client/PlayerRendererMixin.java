package com.nanobuntv.retro64plus.mixin.client;

import com.nanobuntv.retro64plus.client.MarioRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.9 {
/*import com.nanobuntv.retro64plus.client.MarioRenderStateAccess;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
*///?} else {
import com.mojang.blaze3d.vertex.PoseStack;
import com.nanobuntv.retro64plus.client.Draw;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
//?}

/**
 * A player who is Mario is drawn as Mario (client/MarioRenderer) instead of the vanilla model, with their name tag as
 * vanilla draws it. Up to 1.21.8 PlayerRenderer.render draws (this extends its parent only to reach the protected
 * name-tag methods); from 1.21.9 the avatar renderer's state remembers the player, and LivingEntityRendererMixin
 * draws Mario at submit time.
 */
//? if >=1.21.9 {
/*@Mixin(AvatarRenderer.class)
abstract class PlayerRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void retro64plus$rememberPlayer(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        ((MarioRenderStateAccess) state).retro64plus$set(entity instanceof AbstractClientPlayer player ? player : null,
                partialTick, MarioRenderer.inInventory());
    }
}
*///?} else {
@Mixin(PlayerRenderer.class)
abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> model, float shadow) {
        super(context, model, shadow);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void retro64plus$renderMario(AbstractClientPlayer player, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!MarioRenderer.renderPlayer(player, partialTick, pose, Draw.of(buffers), light)) return;
        if (shouldShowName(player)) renderNameTag(player, player.getDisplayName(), pose, buffers, light, partialTick);
        ci.cancel();
    }
}
//?}
