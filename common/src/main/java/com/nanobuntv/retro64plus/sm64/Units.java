package com.nanobuntv.retro64plus.sm64;

/**
 * Conversions between Minecraft and SM64. SM64 units are 1/110 of a block (upstream's SCALE_FACTOR; Mario, 160 units
 * tall in SM64, is then about 1.45 blocks). Axes are the same in both games (y up, right-handed); SM64's yaw 0 faces
 * +z and grows towards +x.
 */
public final class Units {
    public static final float PER_BLOCK = 110f;

    private Units() {}

    public static float toSm64(double blocks) {
        return (float) (blocks * PER_BLOCK);
    }

    public static double toBlocks(float units) {
        return units / PER_BLOCK;
    }

    /**
     * The argument {@code sm64_mChar_set_angle} expects for a yaw in radians: the library multiplies by
     * 32768 × 3.14159, so radians / π² (upstream's "/ 9.5" approximated this).
     */
    public static float nativeAngle(double radians) {
        return (float) (radians / (Math.PI * Math.PI));
    }
}
