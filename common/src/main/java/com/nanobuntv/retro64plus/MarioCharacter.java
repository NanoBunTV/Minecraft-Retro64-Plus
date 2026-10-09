package com.nanobuntv.retro64plus;

import java.util.Locale;

/**
 * Who you play as (D15, D27): sent with every pose as its model byte, so other players draw the same. Mario and Luigi
 * are libsm64's Mario model (Luigi recoloured, D33); {@link #SKIN} is a blocky body in the player's own skin on SM64's
 * skeleton (D28).
 */
public enum MarioCharacter {
    MARIO,
    LUIGI,
    SKIN;

    private static final MarioCharacter[] VALUES = values();

    public int id() {
        return ordinal();
    }

    /** The character with this id; an unknown id (from the network) is Mario. */
    public static MarioCharacter byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : MARIO;
    }

    /** The name used in settings files ("mario", "luigi", "skin"). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The character this settings name means, or null for an unknown one. */
    public static MarioCharacter byKey(String key) {
        for (MarioCharacter c : VALUES) {
            if (c.key().equalsIgnoreCase(key.strip())) return c;
        }
        return null;
    }

    /** The next one in order, wrapping (the character key, D27). */
    public MarioCharacter next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    /** Bit {@code id} set: a set of characters as one int (the server's allowed characters, D34). */
    public int bit() {
        return 1 << ordinal();
    }

    public static final int ALL_BITS = (1 << VALUES.length) - 1;
}
