package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nanobuntv.retro64plus.enemies.KoopaShellEntity;
import com.nanobuntv.retro64plus.sm64.RomModels;
import com.nanobuntv.retro64plus.sm64.Units;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** A Koopa shell lying about (M4 checkpoint 3): the ROM's shell, spinning on the spot as SM64's does. */
public class KoopaShellRenderer extends ModEntityRenderer<KoopaShellEntity> {
    public KoopaShellRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.4f;
    }

    @Override
    protected void draw(KoopaShellEntity shell, float partialTick, PoseStack poseStack, Draw buffers, int light) {
        RomModels.Model model = RomModelRenderer.model(RomModels.Kind.KOOPA_SHELL);
        if (model != null) {
            poseStack.pushPose();
            float spin = (shell.tickCount + partialTick) * KoopaShellEntity.SPIN_PER_TICK;
            poseStack.mulPose(Axis.YP.rotationDegrees(-shell.getYRot() + spin));
            float s = 1 / Units.PER_BLOCK;
            poseStack.scale(s, s, s);
            RomModelRenderer.render(model, null, 0, 0, poseStack, buffers, light, OverlayTexture.NO_OVERLAY, 1);
            poseStack.popPose();
        }
    }
}
