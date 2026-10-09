package com.nanobuntv.retro64plus.fabric.platform;

import com.nanobuntv.retro64plus.platform.ClientEvents;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public final class FabricClientEvents implements ClientEvents {
    @Override
    public void onTickEnd(Runnable listener) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> listener.run());
    }

    @Override
    public void registerKeyMapping(KeyMapping key) {
        KeyBindingHelper.registerKeyBinding(key);
    }

    @Override
    public <T extends Entity> void registerEntityRenderer(Supplier<EntityType<T>> type,
            EntityRendererProvider<T> renderer) {
        EntityRendererRegistry.register(type.get(), renderer);
    }

    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> void registerBlockEntityRenderer(
            Supplier<net.minecraft.world.level.block.entity.BlockEntityType<T>> type,
            java.util.function.Function<net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context,
                    com.nanobuntv.retro64plus.client.ModBlockEntityRenderer<T>> renderer) {
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(type.get(), context -> renderer.apply(context));
    }
}
