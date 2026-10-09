package com.nanobuntv.retro64plus.sm64;

/** Collision surface types and terrain types (the SM64 decompilation's surface_terrains.h), as used so far. */
public final class Surfaces {
    // Surface types: how a floor behaves.
    public static final short DEFAULT = 0x0000;
    /** Lava: Mario bounces off with his rear on fire. */
    public static final short BURNING = 0x0001;
    public static final short VERY_SLIPPERY = 0x0013;
    public static final short SLIPPERY = 0x0014;
    public static final short NOT_SLIPPERY = 0x0015;
    public static final short SHALLOW_QUICKSAND = 0x0021;
    public static final short DEEP_QUICKSAND = 0x0022;
    public static final short INSTANT_QUICKSAND = 0x0023;
    public static final short ICE = 0x002E;
    /** SM64's vanish cap walls: Mario walks through their sides with the Vanish cap (D43). */
    public static final short VANISH_CAP_WALLS = 0x007B;

    // Terrain types: which footstep and landing sounds play.
    public static final short TERRAIN_GRASS = 0;
    public static final short TERRAIN_STONE = 1;
    public static final short TERRAIN_SNOW = 2;
    public static final short TERRAIN_SAND = 3;
    public static final short TERRAIN_SPOOKY = 4;
    public static final short TERRAIN_WATER = 5;
    public static final short TERRAIN_SLIDE = 6;

    private Surfaces() {}
}
