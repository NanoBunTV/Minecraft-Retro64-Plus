package com.nanobuntv.retro64plus.caps;

import org.jetbrains.annotations.Nullable;

/**
 * SM64's special caps (D17): the flag each sets on Mario, how long it lasts (the decompilation's timers at 30 frames a
 * second: Wing 1800 frames, Metal and Vanish 600) and the music it plays. Plain Java.
 */
public enum MarioCap {
    WING("wing_cap", 0x08, 60 * 20, 0x0E),
    METAL("metal_cap", 0x04, 20 * 20, 0x0F),
    VANISH("vanish_cap", 0x02, 20 * 20, 0x0E);

    /** SM64's end-of-timer flicker: bit n set hides the cap with n frames left (decomp mario.c, sCapFlickerFrames). */
    public static final long FLICKER_FRAMES = 0x4444449249255555L;

    public final String itemName;
    /** {@code MARIO_WING_CAP} etc. */
    public final int flag;
    /** How long it lasts, in Minecraft ticks. */
    public final int ticks;
    /** Its music: SEQ_EVENT_POWERUP (wing, vanish) or SEQ_EVENT_METAL_CAP. */
    public final int music;

    MarioCap(String itemName, int flag, int ticks, int music) {
        this.itemName = itemName;
        this.flag = flag;
        this.ticks = ticks;
        this.music = music;
    }

    public static @Nullable MarioCap byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : null;
    }

    /** Whether a cap with {@code framesLeft} SM64 frames to go is hidden this frame. */
    public static boolean flickersOff(int framesLeft) {
        return framesLeft >= 0 && framesLeft < 64 && (FLICKER_FRAMES >>> framesLeft & 1) != 0;
    }
}
