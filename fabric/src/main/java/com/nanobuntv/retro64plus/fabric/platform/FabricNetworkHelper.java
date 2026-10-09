package com.nanobuntv.retro64plus.fabric.platform;

import com.nanobuntv.retro64plus.platform.NetworkHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Payload types are registered during common init. Client receivers can only be registered from the client
 * initializer, so they wait in a list until {@link #registerClientReceivers()}. Handlers run on the main thread.
 */
public final class FabricNetworkHelper implements NetworkHelper {
    private static final List<Runnable> CLIENT_RECEIVERS = new ArrayList<>();

    @Override
    public <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        PayloadTypeRegistry.playC2S().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload, context.player()));
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
        PayloadTypeRegistry.playS2C().register(type, codec);
        CLIENT_RECEIVERS.add(() -> ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload)));
    }

    /** Called from the client initializer. */
    public static void registerClientReceivers() {
        CLIENT_RECEIVERS.forEach(Runnable::run);
        CLIENT_RECEIVERS.clear();
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void sendToTracking(Entity entity, CustomPacketPayload payload) {
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            if (player != entity) ServerPlayNetworking.send(player, payload);
        }
    }
}
