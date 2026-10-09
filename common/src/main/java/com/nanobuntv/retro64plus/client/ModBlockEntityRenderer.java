package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
//? if >=1.21.9 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
*///?} else {
import net.minecraft.client.renderer.MultiBufferSource;
//?}

/**
 * The mod's block entity renderers, each a {@link #draw} into a {@link Draw} on every Minecraft version (Porting.md,
 * Cartridge's). From 1.21.9 Minecraft extracts a render state and later submits it: the state here carries the block
 * entity itself, and {@link RecordingBuffers} hands what {@link #draw} draws to the collector.
 */
//? if >=1.21.9 {
/*public abstract class ModBlockEntityRenderer<T extends BlockEntity>
        implements BlockEntityRenderer<T, ModBlockEntityRenderer.State<T>> {
    public static final class State<T extends BlockEntity> extends BlockEntityRenderState {
        @Nullable T blockEntity;
        float partialTick;
    }

    @Override
    public State<T> createRenderState() {
        return new State<>();
    }

    @Override
    public void extractRenderState(T blockEntity, State<T> state, float partialTick, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, camera, breaking);
        state.blockEntity = blockEntity;
        state.partialTick = partialTick;
    }

    @Override
    public void submit(State<T> state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        T blockEntity = state.blockEntity;
        state.blockEntity = null;
        if (blockEntity == null) return;
        RecordingBuffers buffers = new RecordingBuffers(collector);
        draw(blockEntity, state.partialTick, pose, buffers, state.lightCoords, OverlayTexture.NO_OVERLAY);
        buffers.submit();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return drawsOffScreen();
    }

*///?} else {
public abstract class ModBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    @Override
    public final void render(T blockEntity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        draw(blockEntity, partialTick, pose, Draw.of(buffers), light, overlay);
    }

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return drawsOffScreen();
    }
//?}

    /** Draws the block entity, the pose at its block's corner. */
    public abstract void draw(T blockEntity, float partialTick, PoseStack pose, Draw buffers, int light, int overlay);

    /** Whether it draws past its block, so it's drawn even when its chunk section is off screen. */
    protected boolean drawsOffScreen() {
        return false;
    }
}
