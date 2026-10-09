package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nanobuntv.retro64plus.sm64.RomModels;
import com.nanobuntv.retro64plus.sm64.Units;
import com.nanobuntv.retro64plus.stars.PowerStarEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws a Power Star: SM64's spinning 3D star from the ROM (M4, D79), glowing and bobbing; before the library has loaded
 * (or without a ROM), the HUD's star picture (the mod's own without the ROM) as a camera-facing sprite (D64). Only its
 * owner sees it (D67).
 */
public final class PowerStarRenderer extends ModEntityRenderer<PowerStarEntity> {
    private static final float SIZE = 0.9f;
    /** The ROM star's object scale: SM64's is 128 units across, this makes it about 0.9 blocks. */
    private static final float MODEL_SCALE = 0.75f;

    public PowerStarRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    //? if >=26.3 {
    /*@Override
    public boolean shouldRender(PowerStarEntity star, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z,
            float partialTick) {
        return visible(star, frustum, super.shouldRender(star, frustum, x, y, z, partialTick));
    }
    *///?} else {
    @Override
    public boolean shouldRender(PowerStarEntity star, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return visible(star, frustum, super.shouldRender(star, frustum, x, y, z));
    }
    //?}

    private static boolean visible(PowerStarEntity star, Frustum frustum, boolean seen) {
        var player = Minecraft.getInstance().player;
        return player != null && player.getUUID().equals(star.owner()) && seen;
    }

    @Override
    protected void draw(PowerStarEntity star, float partialTick, PoseStack poseStack, Draw buffers, int light) {
        float time = star.tickCount + partialTick;
        RomModels.Model model = RomModelRenderer.model(RomModels.Kind.STAR);
        if (model != null) {
            // SM64's star (D79): spins 0x800 a frame at 30 Hz, lit by its own shading.
            poseStack.pushPose();
            poseStack.translate(0, star.getBbHeight() / 2 + 0.08f * Mth.sin(time * 0.12f), 0);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 1.5f * 11.25f));
            float scale = MODEL_SCALE / Units.PER_BLOCK;
            poseStack.scale(scale, scale, scale);
            RomModelRenderer.render(model, null, 0, 0, poseStack, buffers, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, 1);
            poseStack.popPose();
            return;
        }
        float u0 = 16f * CoinArt.GLYPH_STAR / CoinArt.WIDTH, u1 = 16f * (CoinArt.GLYPH_STAR + 1) / CoinArt.WIDTH;
        float v0 = (float) CoinArt.GLYPH_Y / CoinArt.HEIGHT, v1 = (CoinArt.GLYPH_Y + 16f) / CoinArt.HEIGHT;
        poseStack.pushPose();
        poseStack.translate(0, star.getBbHeight() / 2 + 0.08f * Mth.sin(time * 0.12f), 0);
        poseStack.mulPose(ClientCompat.cameraRotation());
        poseStack.scale(SIZE, SIZE, SIZE);
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.entityCutoutNoCull(CoinArt.texture()));
        PoseStack.Pose pose = poseStack.last();
        int glow = LightTexture.FULL_BRIGHT;
        vertex(out, pose, -0.5f, -0.5f, u0, v1, glow);
        vertex(out, pose, 0.5f, -0.5f, u1, v1, glow);
        vertex(out, pose, 0.5f, 0.5f, u1, v0, glow);
        vertex(out, pose, -0.5f, 0.5f, u0, v0, glow);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v, int light) {
        out.addVertex(pose, x, y, 0).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0, 1, 0);
    }

}
