package com.nanobuntv.retro64plus.server;

import com.nanobuntv.retro64plus.MarioHooks;
import com.nanobuntv.retro64plus.caps.MarioCap;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import com.nanobuntv.retro64plus.net.Payloads;
import com.nanobuntv.retro64plus.platform.Services;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;

/**
 * The caps Marios wear (D17, D44), owned by the server: one timer per cap (game time it ends), so caps combine. The
 * wearer is told on every change ({@link Payloads.CapState}); everyone else sees them through the pose. What the caps
 * do on the server: Metal's immunity (D42) and Vanish's mobs looking away (D43, mixin/LivingEntityMixin). Caps come
 * off when the player dies or stops being Mario. Server thread (the map is read from mob AI on it too).
 */
public final class ServerCaps {
    private static final Map<UUID, long[]> ENDS = new ConcurrentHashMap<>();

    private ServerCaps() {}

    /** Puts {@code cap} on (again: its timer restarts, as in SM64); returns how long it lasts, stars counted (D66). */
    public static long start(ServerPlayer player, MarioCap cap) {
        long[] ends = ENDS.computeIfAbsent(player.getUUID(), uuid -> new long[MarioCap.values().length]);
        long ticks = duration(player, cap);
        ends[cap.ordinal()] = player.level().getGameTime() + ticks;
        sync(player);
        return ticks;
    }

    /** {@code cap}'s timer for {@code player}: longer with more Power Stars (D62, D66). */
    public static long duration(ServerPlayer player, MarioCap cap) {
        return Math.round(cap.ticks * Retro64PlusConfig.server().capDurationMultiplier(ServerStars.count(player)));
    }

    public static boolean has(Player player, MarioCap cap) {
        long[] ends = ENDS.get(player.getUUID());
        return ends != null && ends[cap.ordinal()] > player.level().getGameTime();
    }

    /** End of every server player tick: caps run out, or come off if the player isn't Mario any more. */
    public static void tick(ServerPlayer player) {
        long[] ends = ENDS.get(player.getUUID());
        if (ends == null) return;
        if (!MarioHooks.isServerMario(player) || !player.isAlive()) {
            clear(player);
            return;
        }
        long now = player.level().getGameTime();
        boolean changed = false, any = false;
        for (int i = 0; i < ends.length; i++) {
            if (ends[i] != 0 && ends[i] <= now) {
                ends[i] = 0;
                changed = true;
            }
            any |= ends[i] != 0;
        }
        if (has(player, MarioCap.METAL)) {
            // Metal Mario doesn't burn or run out of air (D42).
            player.clearFire();
            player.setAirSupply(player.getMaxAirSupply());
        }
        if (!any) ENDS.remove(player.getUUID());
        if (changed) sync(player);
    }

    /** All caps off (death, turning back, leaving). */
    public static void clear(ServerPlayer player) {
        if (ENDS.remove(player.getUUID()) != null) sync(player);
    }

    public static void forget(ServerPlayer player) {
        ENDS.remove(player.getUUID());
    }

    /**
     * Metal Mario's immunity (D42), SM64-like: hits, fire, lava, explosions, falls, drowning and poison all bounce off.
     * The void, {@code /kill} (both bypass invulnerability) and starving still get him, and so do the world's limits:
     * the world border, being stuck inside blocks and crowding.
     */
    public static boolean blocksDamage(Player player, DamageSource source) {
        if (!has(player, MarioCap.METAL)) return false;
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypes.STARVE)
                && !source.is(DamageTypes.OUTSIDE_BORDER) && !source.is(DamageTypes.IN_WALL)
                && !source.is(DamageTypes.CRAMMING);
    }

    private static void sync(ServerPlayer player) {
        long[] ends = ENDS.getOrDefault(player.getUUID(), new long[MarioCap.values().length]);
        Services.NETWORK.sendToPlayer(player, new Payloads.CapState(ends[0], ends[1], ends[2]));
    }
}
