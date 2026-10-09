package com.nanobuntv.retro64plus.neoforge.platform;

import com.nanobuntv.retro64plus.platform.ClientEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class NeoForgeClientEvents implements ClientEvents {
    /** Key bindings registered during client init, handed to NeoForge's registration event later. */
    private static final List<KeyMapping> KEYS = new ArrayList<>();
    /** Entity renderers registered during client init, likewise. */
    private static final List<Consumer<EntityRenderersEvent.RegisterRenderers>> RENDERERS = new ArrayList<>();

    /** Called from the client mod constructor, before common client init. */
    public static void attach(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> KEYS.forEach(event::register));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> RENDERERS.forEach(r -> r.accept(event)));
    }

    @Override
    public void onTickEnd(Runnable listener) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> listener.run());
    }

    @Override
    public void registerKeyMapping(KeyMapping key) {
        KEYS.add(key);
    }

    @Override
    public <T extends Entity> void registerEntityRenderer(Supplier<EntityType<T>> type,
            EntityRendererProvider<T> renderer) {
        RENDERERS.add(event -> event.registerEntityRenderer(type.get(), renderer));
    }

    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> void registerBlockEntityRenderer(
            Supplier<net.minecraft.world.level.block.entity.BlockEntityType<T>> type,
            java.util.function.Function<net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context,
                    com.nanobuntv.retro64plus.client.ModBlockEntityRenderer<T>> renderer) {
        RENDERERS.add(event -> event.registerBlockEntityRenderer(type.get(), context -> renderer.apply(context)));
    }
}
