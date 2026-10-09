package com.nanobuntv.retro64plus;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Who is Mario, as the common mixins ask it. Free of client classes, so a dedicated server can load it. Each side keeps
 * its own set (an integrated server shares the JVM with its client): the server's comes from the clients' state
 * payloads (server/ServerMarios), the client's from the server's relays (client/RemoteMarios) plus its own player.
 */
public final class MarioHooks {
    /** Mario's hitbox: upstream meant 0.5 × 1.3 (Mario is about 1.45 blocks tall in game) but never registered it. */
    public static final EntityDimensions MARIO_DIMENSIONS = EntityDimensions.scalable(0.5f, 1.3f);

    /** The client's own player while it's Mario, else null. Its movement comes from libsm64, not vanilla physics. */
    private static volatile @Nullable Player localMario;
    /** Players the server treats as Mario. */
    private static final Set<UUID> SERVER_MARIOS = ConcurrentHashMap.newKeySet();
    /** Other players this client knows are Mario. */
    private static final Set<UUID> CLIENT_MARIOS = ConcurrentHashMap.newKeySet();

    private MarioHooks() {}

    public static void setLocalMario(@Nullable Player player) {
        localMario = player;
    }

    public static boolean isLocalMario(Player player) {
        return player == localMario;
    }

    public static void setServerMario(UUID player, boolean mario) {
        if (mario) SERVER_MARIOS.add(player);
        else SERVER_MARIOS.remove(player);
    }

    public static boolean isServerMario(Player player) {
        return SERVER_MARIOS.contains(player.getUUID());
    }

    public static void setClientMario(UUID player, boolean mario) {
        if (mario) CLIENT_MARIOS.add(player);
        else CLIENT_MARIOS.remove(player);
    }

    public static void clearClientMarios() {
        CLIENT_MARIOS.clear();
    }

    /** Whether {@code player} is Mario on the side this copy of it lives on. */
    public static boolean isMario(Player player) {
        if (player.level() == null) return false; // during construction
        return player.level().isClientSide()
                ? player == localMario || CLIENT_MARIOS.contains(player.getUUID())
                : SERVER_MARIOS.contains(player.getUUID());
    }
}
