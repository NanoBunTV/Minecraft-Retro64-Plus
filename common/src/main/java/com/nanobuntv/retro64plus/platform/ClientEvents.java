package com.nanobuntv.retro64plus.platform;

import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Client-side hooks (D8). Loaded only on the client, through {@link ClientServices}. */
public interface ClientEvents {
    /** At the end of every client tick (20 per second), on the render thread. */
    void onTickEnd(Runnable listener);

    /** Adds a key binding to Minecraft's Controls screen. Call during client init. */
    void registerKeyMapping(KeyMapping key);

    /** Gives an entity type its renderer. Call during client init. */
    <T extends Entity> void registerEntityRenderer(Supplier<EntityType<T>> type, EntityRendererProvider<T> renderer);

    /** A block entity's renderer (M4 checkpoint 4). */
    <T extends net.minecraft.world.level.block.entity.BlockEntity> void registerBlockEntityRenderer(
            Supplier<net.minecraft.world.level.block.entity.BlockEntityType<T>> type,
            java.util.function.Function<net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context,
                    com.nanobuntv.retro64plus.client.ModBlockEntityRenderer<T>> renderer);
}
