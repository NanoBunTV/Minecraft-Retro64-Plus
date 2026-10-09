package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.MarioHooks;
import com.nanobuntv.retro64plus.net.MarioUpdate;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import com.nanobuntv.retro64plus.sm64.lib.Libsm64;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Other players' Marios on this client (M1 checkpoint 3), from the server's relays: who is Mario (for their hitbox,
 * MarioHooks) and a puppet each to draw them with. Each pose is drawn into the puppet as it arrives (20 a second, once
 * per pose: upstream re-ticked every remote Mario once per remote player per frame). A player who goes out of range
 * stops getting poses and is dropped after {@link #STALE_TICKS}. Without the ROM loaded, remote players keep the
 * vanilla model; the first pose starts loading it quietly. Each also has a libsm64 sound source, so the sounds that
 * come with its poses are heard from where the player is (D32, {@link MarioAudio}). Render thread only.
 */
final class RemoteMarios {
    private static final int STALE_TICKS = 40;

    private static final class Remote {
        final UUID uuid;
        @Nullable MarioPuppet puppet;
        long lastPose;
        /** Its sound source, or -1 when all are taken. */
        int soundSource = -1;

        Remote(UUID uuid) {
            this.uuid = uuid;
        }
    }

    private static final Map<Integer, Remote> BY_ENTITY = new HashMap<>();
    private static final BitSet SOUND_SOURCES = new BitSet(Libsm64.SOUND_SOURCES);
    private static long ticks;

    private RemoteMarios() {}

    static void onUpdate(int entityId, Optional<MarioUpdate> update) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(entityId);
        if (!(entity instanceof Player player) || player == mc.player) return;
        if (update.isEmpty()) {
            remove(entityId);
            return;
        }
        Remote remote = BY_ENTITY.get(entityId);
        if (remote == null) {
            remote = new Remote(player.getUUID());
            int source = SOUND_SOURCES.nextClearBit(0);
            if (source < MarioAudio.FIRST_ENEMY_SOURCE) {
                SOUND_SOURCES.set(source);
                remote.soundSource = source;
            }
            BY_ENTITY.put(entityId, remote);
            MarioHooks.setClientMario(remote.uuid, true);
            player.refreshDimensions();
        }
        remote.lastPose = ticks;
        if (LibSM64.status() == LibSM64.Status.NOT_STARTED) Retro64PlusClient.loadQuietly();
        if (LibSM64.status() != LibSM64.Status.READY) return;
        if (remote.puppet == null) {
            MarioTexture.ensureRegistered();
            remote.puppet = MarioPuppet.create();
            if (remote.puppet == null) return;
        }
        remote.puppet.show(update.get().pose());
        if (!update.get().sounds().isEmpty()) {
            MarioAudio.playRemote(remote.soundSource, player.position(), update.get().sounds());
        }
    }

    /** End of every client tick: drops players gone quiet, and everyone when the world goes away. */
    static void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        for (Iterator<Map.Entry<Integer, Remote>> it = BY_ENTITY.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Remote> entry = it.next();
            Entity entity = mc.level.getEntity(entry.getKey());
            if (ticks - entry.getValue().lastPose > STALE_TICKS || entity == null) {
                release(entry.getValue(), entity);
                it.remove();
            } else {
                MarioAudio.moveRemote(entry.getValue().soundSource, entity.position());
            }
        }
    }

    /** The puppet to draw {@code player} with, or null to draw the vanilla model. */
    static @Nullable MarioPuppet puppet(Player player) {
        Remote remote = BY_ENTITY.get(player.getId());
        return remote == null ? null : remote.puppet;
    }

    private static void remove(int entityId) {
        Remote remote = BY_ENTITY.remove(entityId);
        if (remote != null && Minecraft.getInstance().level != null) {
            release(remote, Minecraft.getInstance().level.getEntity(entityId));
        }
    }

    private static void release(Remote remote, @Nullable Entity entity) {
        MarioHooks.setClientMario(remote.uuid, false);
        if (entity != null) entity.refreshDimensions();
        if (remote.puppet != null) remote.puppet.delete();
        freeSoundSource(remote);
    }

    private static void freeSoundSource(Remote remote) {
        if (remote.soundSource < 0) return;
        MarioAudio.releaseRemote(remote.soundSource);
        SOUND_SOURCES.clear(remote.soundSource);
        remote.soundSource = -1;
    }

    private static void clear() {
        if (BY_ENTITY.isEmpty()) return;
        for (Remote remote : BY_ENTITY.values()) {
            if (remote.puppet != null) remote.puppet.delete();
            freeSoundSource(remote);
        }
        BY_ENTITY.clear();
        MarioHooks.clearClientMarios();
    }
}
