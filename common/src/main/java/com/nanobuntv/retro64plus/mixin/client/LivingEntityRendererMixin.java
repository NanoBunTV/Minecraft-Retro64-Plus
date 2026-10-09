package com.nanobuntv.retro64plus.mixin.client;

//? if >=1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.nanobuntv.retro64plus.client.MarioRenderStateAccess;
import com.nanobuntv.retro64plus.client.MarioRenderer;
import com.nanobuntv.retro64plus.client.RecordingBuffers;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * From 1.21.9: a player who is Mario is submitted as Mario (client/MarioRenderer) instead of the vanilla model; the
 * name tag and leashes follow as {@code EntityRenderer.submit} does them. The player renderer inherits this submit
 * from LivingEntityRenderer; the state names its player (PlayerRendererMixin).
 ^/
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>>
        extends EntityRenderer<T, S> {
    private LivingEntityRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    //? if >=26.1 {
    /^@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    ^///?} else {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true)
    //?}
    private void retro64plus$submitMario(S state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera,
            CallbackInfo ci) {
        if (!(state instanceof MarioRenderStateAccess access)) return;
        AbstractClientPlayer player = access.retro64plus$player();
        if (player == null) return;
        RecordingBuffers buffers = new RecordingBuffers(collector);
        if (!MarioRenderer.renderPlayer(player, access.retro64plus$partialTick(), pose, buffers, state.lightCoords,
                access.retro64plus$inInventory())) {
            return;
        }
        buffers.submit();
        super.submit(state, pose, collector, camera);
        ci.cancel();
    }
}
*///?}
