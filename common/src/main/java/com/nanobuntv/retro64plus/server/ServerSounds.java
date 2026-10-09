package com.nanobuntv.retro64plus.server;

import com.nanobuntv.retro64plus.net.Payloads;
import com.nanobuntv.retro64plus.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * SM64's sounds for the mod's own events (D108), played by every client near {@code near}: the server only says which
 * sound and where. Sound bits are the decomp's {@code audio_defines.h} values with the "starting" status bit set.
 */
public final class ServerSounds {
    /** SOUND_ARG_LOAD(3, 8, 0x11, 0x80, 8): a coin picked up; also a Coin Shop purchase. */
    public static final int COIN = 0x38118081;
    /** SOUND_ARG_LOAD(7, 0, 0x1E, 0xFF, 8): SM64's cap pickup (interact_cap). */
    public static final int CAP = 0x701EFF81;
    /** SOUND_ARG_LOAD(2, 4, 0x0C, 0x80, 8): Mario's "Here we go!", also from interact_cap. */
    public static final int HERE_WE_GO = 0x240C8081;
    /** SOUND_ARG_LOAD(3, 0, 0x58, 0xFF, 8): the 1-up jingle, for the Recharge Mushroom. */
    public static final int ONE_UP = 0x3058FF81;

    private ServerSounds() {}

    /**
     * Plays {@code sound} at {@code at} (blocks) for the players with {@code near} loaded, and for {@code near} itself if
     * it's a player.
     */
    public static void play(Entity near, Vec3 at, int sound) {
        Payloads.Sm64Sound payload = new Payloads.Sm64Sound(at.x, at.y, at.z, sound);
        Services.NETWORK.sendToTracking(near, payload);
        if (near instanceof ServerPlayer player) Services.NETWORK.sendToPlayer(player, payload);
    }
}
