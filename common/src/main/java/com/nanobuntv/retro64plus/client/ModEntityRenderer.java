package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
//? if >=1.21.9 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
*///?} else {
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
//?}

/**
 * The mod's entity renderers, each a {@link #draw} into a {@link Draw} on every Minecraft version (Porting.md). Up to
 * 1.21.1 Minecraft's own {@code render} hands it the entity and a buffer source. From 1.21.2 Minecraft extracts a
 * render state and later submits it: the state here carries the entity itself (both happen on the render thread in the
 * same frame), and from 1.21.9 {@link RecordingBuffers} hands what {@link #draw} draws to the collector.
 */
//? if >=1.21.9 {
/*public abstract class ModEntityRenderer<T extends Entity> extends EntityRenderer<T, ModEntityRenderer.State<T>> {
    public static final class State<T extends Entity> extends EntityRenderState {
        @Nullable T entity;
        float partialTick;
    }

    protected ModEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State<T> createRenderState() {
        return new State<>();
    }

    @Override
    public void extractRenderState(T entity, State<T> state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.entity = entity;
        state.partialTick = partialTick;
    }

    @Override
    public void submit(State<T> state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        T entity = state.entity;
        if (entity != null) {
            RecordingBuffers buffers = new RecordingBuffers(collector);
            draw(entity, state.partialTick, pose, buffers, state.lightCoords);
            buffers.submit();
        }
        super.submit(state, pose, collector, camera);
    }

*///?} else {
public abstract class ModEntityRenderer<T extends Entity> extends EntityRenderer<T> {
    protected ModEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        draw(entity, partialTick, pose, Draw.of(buffers), light);
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS; // unused: each draw names its own textures
    }
//?}

    /** Draws the entity, the pose at its position. */
    protected abstract void draw(T entity, float partialTick, PoseStack pose, Draw buffers, int light);

    /** The overlay a living entity is drawn with: red while hurt or dying, as vanilla's. */
    protected static int overlay(@Nullable Entity entity) {
        boolean hurt = entity instanceof LivingEntity living && (living.hurtTime > 0 || living.deathTime > 0);
        return OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(hurt));
    }
}
