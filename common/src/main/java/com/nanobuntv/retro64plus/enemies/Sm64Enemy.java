package com.nanobuntv.retro64plus.enemies;

import com.nanobuntv.retro64plus.combat.MarioAttack;
import net.minecraft.server.level.ServerPlayer;

/**
 * An SM64 enemy (M4, D72–D76): it drops SM64's coins itself, for any killer (so the generic mob coins of D52 skip it),
 * and handles Mario's attacks the SM64 way (D74).
 */
public interface Sm64Enemy {
    /** Coins it drops when it dies (D76). */
    int coins();

    /**
     * One of Mario's attacks reached it (checked by the server). True if the enemy dealt with it itself; false for the
     * normal damage and knockback.
     */
    boolean onMarioAttack(ServerPlayer player, MarioAttack attack);
}
