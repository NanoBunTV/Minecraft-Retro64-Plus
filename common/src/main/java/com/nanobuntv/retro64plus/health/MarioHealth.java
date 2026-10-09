package com.nanobuntv.retro64plus.health;

/**
 * Minecraft health and SM64's power meter (D36): health stays Minecraft's (armor, potions and every mod's damage keep
 * working) and the meter shows it as 8 segments of max health / 8 each (2.5 HP with 20). Plain Java, unit-tested.
 *
 * <p>SM64's health units: {@link #SEGMENT} per segment, {@link #FULL} full; below {@link #SEGMENT} Mario dies.
 */
public final class MarioHealth {
    public static final int SEGMENTS = 8;
    public static final int SEGMENT = 0x100;
    public static final int FULL = 0x880;
    /** What libsm64 gets once the player is dead: SM64 then plays a death action (standing, drowning, ...). */
    public static final short DEAD = 0xFF;
    /**
     * SM64's healing at the water surface (D38): {@code 0x1A} units a frame while swimming within 120 units of the
     * surface (decomp mario.c, update_mario_health), as segments per Minecraft tick (1.5 SM64 frames).
     */
    public static final double SURFACE_HEAL_SEGMENTS_PER_TICK = 0x1A * 1.5 / SEGMENT;
    /** How far below the surface (blocks) still counts as at the surface: SM64's 120 units. */
    public static final double SURFACE_DEPTH = 120 / 110.0;
    /** A coin's healing (D54): half of SM64's segment a coin. */
    public static final int COIN_HEAL = SEGMENT / 2;
    /**
     * How fast coin healing is given: SM64's {@code 0x40} units a frame (decomp mario.c, healCounter), as units per
     * Minecraft tick.
     */
    public static final double COIN_HEAL_PER_TICK = 0x40 * 1.5;

    private MarioHealth() {}

    /** Segments the meter shows: rounded up, so a living player always has at least one. */
    public static int segments(float health, float maxHealth) {
        if (health <= 0 || maxHealth <= 0) return 0;
        return Math.clamp((int) Math.ceil(SEGMENTS * health / maxHealth - 1e-4), 1, SEGMENTS);
    }

    /** The health libsm64 gets: the meter's segments (a living Mario never dies on his own), or {@link #DEAD}. */
    public static short sm64Health(float health, float maxHealth, boolean dead) {
        if (dead) return DEAD;
        return (short) (Math.max(1, segments(health, maxHealth)) * SEGMENT + 0x80);
    }

    /** SM64 damage or healing ({@code units}, {@link #SEGMENT} a segment) as Minecraft health. */
    public static float toHealth(double units, float maxHealth) {
        return (float) (units / SEGMENT * maxHealth / SEGMENTS);
    }

    /** Minecraft damage as whole segments, at least one: picks SM64's soft, normal or hard knockback. */
    public static int toSegments(float damage, float maxHealth) {
        if (maxHealth <= 0) return 1;
        return Math.max(1, Math.round(damage * SEGMENTS / maxHealth));
    }
}
