package com.nanobuntv.retro64plus.caps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** D17: SM64's timers and flicker. */
class MarioCapTest {
    @Test
    void timersAreTheDecompilations() {
        assertEquals(1800, MarioCap.WING.ticks * 3 / 2); // frames
        assertEquals(600, MarioCap.METAL.ticks * 3 / 2);
        assertEquals(600, MarioCap.VANISH.ticks * 3 / 2);
    }

    @Test
    void flickersOnlyNearTheEnd() {
        assertFalse(MarioCap.flickersOff(64));
        assertFalse(MarioCap.flickersOff(1000));
        int hidden = 0;
        for (int f = 0; f < 64; f++) if (MarioCap.flickersOff(f)) hidden++;
        assertEquals(Long.bitCount(MarioCap.FLICKER_FRAMES), hidden);
        // More often at the very end than at 60 frames left.
        int late = 0, early = 0;
        for (int f = 0; f < 16; f++) if (MarioCap.flickersOff(f)) late++;
        for (int f = 48; f < 64; f++) if (MarioCap.flickersOff(f)) early++;
        assertTrue(late > early, late + " vs " + early);
    }
}
