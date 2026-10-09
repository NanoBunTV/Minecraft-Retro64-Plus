package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nanobuntv.retro64plus.enemies.ChainChompEntity;
import com.nanobuntv.retro64plus.enemies.ChompPostBlock;
import com.nanobuntv.retro64plus.sm64.RomModels;
import com.nanobuntv.retro64plus.sm64.Units;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Chain Chomp (M4 checkpoint 4): SM64's model at SM64's size (D96), lifted so it sits on the ground (the model is
 * centered on its origin), chomping; and its chain, SM64's five links from the top of the post to the back of its
 * head, sagging when slack.
 */
public class ChainChompRenderer extends Sm64EnemyRenderer<ChainChompEntity> {
    private static final int LINKS = 5;

    public ChainChompRenderer(EntityRendererProvider.Context context) {
        super(context, RomModels.Kind.CHAIN_CHOMP, 1f, 1.2f);
    }

    @Override
    protected void draw(ChainChompEntity chomp, float partialTick, PoseStack poseStack, Draw buffers, int light) {
        super.draw(chomp, partialTick, poseStack, buffers, light);
        chomp.post().ifPresent(post -> renderChain(chomp, post, partialTick, poseStack, buffers, light));
    }

    private void renderChain(ChainChompEntity chomp, BlockPos post, float partialTick, PoseStack poseStack,
            Draw buffers, int light) {
        RomModels.Model link = RomModelRenderer.model(RomModels.Kind.CHAIN_LINK);
        BlockState state = chomp.level().getBlockState(post);
        if (link == null || !(state.getBlock() instanceof ChompPostBlock)) return;
        Vec3 origin = chomp.getPosition(partialTick);
        Vec3 from = new Vec3(post.getX() + 0.5, post.getY() + ChompPostBlock.top(state.getValue(ChompPostBlock.POUNDS))
                - 0.2, post.getZ() + 0.5);
        double facing = Math.toRadians(chomp.yaw(partialTick));
        Vec3 to = origin.add(Math.sin(facing) * 0.9, ChainChompEntity.MODEL_LIFT - 0.2, -Math.cos(facing) * 0.9);
        double length = from.distanceTo(to);
        double sag = 0.1 + 0.8 * Math.max(0, 1 - length / ChainChompEntity.CHAIN);
        float s = 1 / Units.PER_BLOCK;
        for (int i = 1; i <= LINKS; i++) {
            double t = i / (LINKS + 1.0);
            Vec3 p = from.lerp(to, t).subtract(0, sag * Mth.sin((float) (Math.PI * t)), 0).subtract(origin);
            poseStack.pushPose();
            poseStack.translate(p.x, p.y, p.z);
            poseStack.scale(s, s, s);
            RomModelRenderer.render(link, null, 0, 0, poseStack, buffers, light, OverlayTexture.NO_OVERLAY, 1);
            poseStack.popPose();
        }
    }

    @Override
    protected int animation(ChainChompEntity chomp) {
        return 0;
    }

    @Override
    protected float frame(ChainChompEntity chomp, float partialTick) {
        return Mth.lerp(partialTick, chomp.lastAnimFrames, chomp.animFrames);
    }

    @Override
    protected int animState(ChainChompEntity chomp) {
        return 0;
    }

    @Override
    protected Vec3 offset(ChainChompEntity chomp, float partialTick) {
        return new Vec3(0, ChainChompEntity.MODEL_LIFT, 0);
    }

    /** The chain reaches further than the Chomp's box: keep drawing while the post is in view. */
    //? if >=26.3 {
    /*@Override
    public boolean shouldRender(ChainChompEntity chomp, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z,
            float partialTick) {
        return visible(chomp, frustum, super.shouldRender(chomp, frustum, x, y, z, partialTick));
    }
    *///?} else {
    @Override
    public boolean shouldRender(ChainChompEntity chomp, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return visible(chomp, frustum, super.shouldRender(chomp, frustum, x, y, z));
    }
    //?}

    private static boolean visible(ChainChompEntity chomp, net.minecraft.client.renderer.culling.Frustum frustum,
            boolean seen) {
        if (seen) return true;
        return chomp.post().map(p -> frustum.isVisible(new net.minecraft.world.phys.AABB(p).inflate(1))).orElse(false);
    }
}
