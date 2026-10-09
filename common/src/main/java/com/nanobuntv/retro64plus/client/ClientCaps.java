package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.caps.MarioCap;
import net.minecraft.client.Minecraft;

/**
 * The caps the local player wears (D17, D44): the game time each ends, as the server last said, against the client
 * level's game time (which the server keeps in step). Every SM64 frame they become Mario's cap flags, minus those
 * flickering off near their end, as in SM64. Render thread.
 */
final class ClientCaps {
    private static final long[] END = new long[MarioCap.values().length];

    private ClientCaps() {}

    static void set(long wing, long metal, long vanish) {
        END[MarioCap.WING.ordinal()] = wing;
        END[MarioCap.METAL.ordinal()] = metal;
        END[MarioCap.VANISH.ordinal()] = vanish;
    }

    static void reset() {
        java.util.Arrays.fill(END, 0);
    }

    /** Ticks left on {@code cap} (0: off). */
    static int left(MarioCap cap) {
        long end = END[cap.ordinal()];
        var level = Minecraft.getInstance().level;
        if (end == 0 || level == null) return 0;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, end - level.getGameTime()));
    }

    static boolean has(MarioCap cap) {
        return left(cap) > 0;
    }

    /** Ticks left on the cap that lasts longest (0: none). */
    static int longest() {
        int most = 0;
        for (MarioCap cap : MarioCap.values()) most = Math.max(most, left(cap));
        return most;
    }

    /** {@code MARIO_*_CAP} flags of the caps on. */
    static int flags() {
        int flags = 0;
        for (MarioCap cap : MarioCap.values()) if (has(cap)) flags |= cap.flag;
        return flags;
    }

    /** Caps flickering off this frame ({@code framesIntoTick} SM64 frames after this tick's start). */
    static int hiddenFlags(int framesIntoTick) {
        int hidden = 0;
        for (MarioCap cap : MarioCap.values()) {
            int left = left(cap);
            if (left > 0 && MarioCap.flickersOff(left * 3 / 2 - framesIntoTick)) hidden |= cap.flag;
        }
        return hidden;
    }

    /** The music the caps want: Metal's while it's on, else the power-up music, else none (-1). */
    static int music() {
        if (has(MarioCap.METAL)) return MarioCap.METAL.music;
        for (MarioCap cap : MarioCap.values()) if (has(cap)) return cap.music;
        return -1;
    }

    /** For dev diagnostics. */
    static String debugState() {
        return String.format(java.util.Locale.ROOT, "caps wing %d metal %d vanish %d", left(MarioCap.WING),
                left(MarioCap.METAL), left(MarioCap.VANISH));
    }
}
