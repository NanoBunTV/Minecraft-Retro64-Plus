package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nanobuntv.retro64plus.enemies.ChompPostBlock;
import com.nanobuntv.retro64plus.enemies.ChompPostBlockEntity;
import com.nanobuntv.retro64plus.sm64.RomModels;
import com.nanobuntv.retro64plus.sm64.Units;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/**
 * The Chain Chomp's post (M4 checkpoint 4): SM64's wooden post from the ROM at its Bob-omb Battlefield size (half the
 * model), driven into the ground by each ground pound ({@link ChompPostBlock#top}).
 */
public class ChompPostRenderer extends ModBlockEntityRenderer<ChompPostBlockEntity> {
    /** The post's object scale in SM64. */
    private static final float POST_SCALE = 0.5f;

    public ChompPostRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void draw(ChompPostBlockEntity post, float partialTick, PoseStack poseStack, Draw buffers, int light,
            int overlay) {
        RomModels.Model model = RomModelRenderer.model(RomModels.Kind.WOODEN_POST);
        if (model == null) return;
        int pounds = post.getBlockState().hasProperty(ChompPostBlock.POUNDS)
                ? post.getBlockState().getValue(ChompPostBlock.POUNDS) : 0;
        poseStack.pushPose();
        poseStack.translate(0.5, ChompPostBlock.top(pounds) - ChompPostBlock.TOP_UNITS / Units.PER_BLOCK, 0.5);
        float s = POST_SCALE / Units.PER_BLOCK;
        poseStack.scale(s, s, s);
        RomModelRenderer.render(model, null, 0, 0, poseStack, buffers, light, overlay, 1);
        poseStack.popPose();
    }

    /**
     * The post reaches into the block above, which may be in another chunk section: drawn even when its own section
     * is culled (vanilla's switch; NeoForge also takes the box below).
     */
    @Override
    protected boolean drawsOffScreen() {
        return true;
    }

    /** NeoForge's culling box (its renderer extension; plain Java override, unknown to vanilla): the post is tall. */
    public AABB getRenderBoundingBox(ChompPostBlockEntity post) {
        return new AABB(post.getBlockPos()).expandTowards(0, 1, 0);
    }
}
