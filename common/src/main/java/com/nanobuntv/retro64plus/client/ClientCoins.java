package com.nanobuntv.retro64plus.client;

/** This player's coin count (D50) and Power Star count (D63), as the server last sent them. Render thread. */
public final class ClientCoins {
    private static int coins, stars;

    private ClientCoins() {}

    static void set(int count) {
        coins = Math.max(0, count);
    }

    static void setStars(int count) {
        stars = Math.max(0, count);
    }

    public static int coins() {
        return coins;
    }

    public static int stars() {
        return stars;
    }

    /** Left the world. */
    static void reset() {
        coins = 0;
        stars = 0;
        com.nanobuntv.retro64plus.caps.StarBonus.clientDuration = 1;
        com.nanobuntv.retro64plus.caps.StarBonus.clientRecharge = 1;
    }
}
