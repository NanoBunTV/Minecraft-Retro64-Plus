package com.nanobuntv.retro64plus.health;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** D36: the power meter shows Minecraft's health, 2.5 HP a segment with 20 max. */
class MarioHealthTest {
    @Test
    void segmentsRoundUp() {
        assertEquals(8, MarioHealth.segments(20, 20));
        assertEquals(8, MarioHealth.segments(17.6f, 20)); // more than 7 × 2.5
        assertEquals(7, MarioHealth.segments(17.5f, 20));
        assertEquals(1, MarioHealth.segments(0.5f, 20)); // alive: never an empty meter
        assertEquals(0, MarioHealth.segments(0, 20));
        assertEquals(4, MarioHealth.segments(20, 40)); // Health Boost: same proportions
    }

    @Test
    void sm64HealthKeepsALivingMarioAlive() {
        assertEquals(0x880, MarioHealth.sm64Health(20, 20, false));
        assertEquals(0x180, MarioHealth.sm64Health(0.1f, 20, false));
        assertEquals(0xFF, MarioHealth.sm64Health(0, 20, true));
    }

    @Test
    void conversions() {
        assertEquals(5f, MarioHealth.toHealth(2 * MarioHealth.SEGMENT, 20), 1e-6); // SM64's small fall
        assertEquals(1, MarioHealth.toSegments(1, 20));
        assertEquals(2, MarioHealth.toSegments(6, 20)); // a zombie on normal
        assertEquals(4, MarioHealth.toSegments(10, 20)); // hard knockback
        // Surfacing: SM64's 0x1A a frame is a whole meter in under 3 s.
        double ticks = MarioHealth.SEGMENTS / MarioHealth.SURFACE_HEAL_SEGMENTS_PER_TICK;
        assertEquals(52.5, ticks, 0.1);
    }
}
