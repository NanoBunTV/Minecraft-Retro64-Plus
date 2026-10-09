package com.nanobuntv.retro64plus.platform;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Custom payloads (play phase), from Cartridge's platform layer. Register everything during common init; handlers run
 * on the main thread of the side that receives the payload.
 */
public interface NetworkHelper {
    <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler);

    /** {@code handler} runs only on a client; it must not touch client classes directly (see net/Retro64PlusNetwork). */
    <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler);

    /** Client only. */
    void sendToServer(CustomPacketPayload payload);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** To every player whose client has {@code entity} loaded (not the entity itself). */
    void sendToTracking(Entity entity, CustomPacketPayload payload);
}
