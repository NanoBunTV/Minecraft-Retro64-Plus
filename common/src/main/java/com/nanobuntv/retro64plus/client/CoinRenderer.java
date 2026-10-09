package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nanobuntv.retro64plus.coins.CoinEntity;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/**
 * Draws a coin as SM64 does: a camera-facing sprite cycling through its four spin frames, one every two SM64 frames,
 * tinted yellow, blinking before it despawns. The first coin seen starts loading the ROM quietly (as a remote Mario does).
 */
public final class CoinRenderer extends ModEntityRenderer<CoinEntity> {
    /** SM64's coin is 64 units across. */
    private static final float SIZE = 64 / 110f;
    /** SM64's yellow coin colour (its texture is grey). */
    private static final int RED = 255, GREEN = 220, BLUE = 0;

    private static boolean triedRom;

    public CoinRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.15f;
        shadowStrength = 0.6f;
    }

    @Override
    protected int getBlockLightLevel(CoinEntity coin, BlockPos pos) {
        return Mth.clamp(super.getBlockLightLevel(coin, pos) + 7, 0, 15);
    }

    @Override
    protected void draw(CoinEntity coin, float partialTick, PoseStack poseStack, Draw buffers, int light) {
        if (!triedRom && LibSM64.status() == LibSM64.Status.NOT_STARTED) {
            triedRom = true; // once a game: without a ROM, coins keep the stand-in look
            Retro64PlusClient.loadQuietly();
        }
        // SM64's coins blink every other frame near the end.
        if (coin.level().getGameTime() >= coin.despawnAt() - CoinEntity.BLINK_TICKS && (coin.tickCount & 1) == 0) return;
        // SM64 advances the coin's animation state every 30 Hz frame through 8 states, each spin frame twice
        // (actors/coin/geo.inc.c): a new picture every 2 frames.
        int frame = (int) ((coin.tickCount + partialTick) * 1.5f / 2) & 3;
        float u0 = frame * 32f / CoinArt.WIDTH, u1 = (frame + 1) * 32f / CoinArt.WIDTH, v1 = 32f / CoinArt.HEIGHT;
        poseStack.pushPose();
        poseStack.translate(0, SIZE / 2 - 0.04f, 0);
        poseStack.mulPose(ClientCompat.cameraRotation());
        poseStack.scale(SIZE, SIZE, SIZE);
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.entityCutoutNoCull(CoinArt.texture()));
        PoseStack.Pose pose = poseStack.last();
        vertex(out, pose, -0.5f, -0.5f, u0, v1, light);
        vertex(out, pose, 0.5f, -0.5f, u1, v1, light);
        vertex(out, pose, 0.5f, 0.5f, u1, 0, light);
        vertex(out, pose, -0.5f, 0.5f, u0, 0, light);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v, int light) {
        out.addVertex(pose, x, y, 0).setColor(RED, GREEN, BLUE, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0, 1, 0);
    }

}
