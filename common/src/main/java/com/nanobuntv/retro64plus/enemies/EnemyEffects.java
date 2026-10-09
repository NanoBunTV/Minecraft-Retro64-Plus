package com.nanobuntv.retro64plus.enemies;

import net.minecraft.world.entity.Entity;

/**
 * SM64's sounds for the enemies, played by the client (common code can't reach the client's sound engine, so the
 * client installs itself here at init; a dedicated server keeps the silent default). Sound bits are the decomp's
 * {@code sounds.h} values with the "starting" status bit set, as the native engine expects.
 */
public final class EnemyEffects {
    public static final int GOOMBA_WALK = 0x50200081, GOOMBA_ALERT = 0x502F0081, ENEMY_DEATH_HIGH = 0x5060B081,
            BOBOMB_WALK = 0x50270081, BOBOMB_LIT_FUSE = 0x60086001, BOBOMB_EXPLOSION = 0x802E2081,
            STOMPED = 0x50308081, KOOPA_WALK = 0x50350081, KOOPA_DAMAGE = 0x503EA081, KOOPA_DEATH = 0x5063B081,
            CHAIN_CHOMP_HOP = 0x30390081, CHAIN_CHOMP_LUNGE = 0x303A0081, POUND_WOOD_POST = 0x3065C081;

    /** Plays an SM64 sound from an entity. */
    public interface Player {
        void play(Entity from, int sound);
    }

    private static Player player = (from, sound) -> { };

    private EnemyEffects() {}

    public static void install(Player client) {
        player = client;
    }

    /** Client side only; does nothing on a server. */
    public static void play(Entity from, int sound) {
        if (from.level().isClientSide()) player.play(from, sound);
    }
}
