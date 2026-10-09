package com.nanobuntv.retro64plus.caps;

/**
 * What Power Stars unlock for the caps (D62, D66): each star makes caps last longer and recharge faster by a share,
 * up to a cap (server settings). Plain Java, unit-tested.
 */
public final class StarBonus {
    /**
     * This client's multipliers, as the server last sent them (for tooltips; set on the render thread, read on it).
     * 1 until a server says otherwise.
     */
    public static float clientDuration = 1, clientRecharge = 1;

    private StarBonus() {}

    /** How much longer caps last with {@code stars}: 1 + percent per star, at most 1 + max percent. */
    public static double durationMultiplier(int stars, double percentPerStar, double maxPercent) {
        return 1 + Math.min(Math.max(0, stars) * percentPerStar, maxPercent) / 100;
    }

    /** How much of the recharge time is left with {@code stars}: 1 − percent per star, at least 1 − max percent. */
    public static double rechargeMultiplier(int stars, double percentPerStar, double maxPercent) {
        return Math.max(0, 1 - Math.min(Math.max(0, stars) * percentPerStar, maxPercent) / 100);
    }
}
