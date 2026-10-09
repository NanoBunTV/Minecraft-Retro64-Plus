package com.nanobuntv.retro64plus.caps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** D66: 3 % a star, up to 60 %, for both the caps' duration and their recharge. */
class StarBonusTest {
    @Test
    void durationGrowsUpToTheCap() {
        assertEquals(1.0, StarBonus.durationMultiplier(0, 3, 60), 1e-9);
        assertEquals(1.3, StarBonus.durationMultiplier(10, 3, 60), 1e-9);
        assertEquals(1.6, StarBonus.durationMultiplier(20, 3, 60), 1e-9);
        assertEquals(1.6, StarBonus.durationMultiplier(500, 3, 60), 1e-9);
        assertEquals(1.0, StarBonus.durationMultiplier(-4, 3, 60), 1e-9);
    }

    @Test
    void rechargeShrinksDownToTheCap() {
        assertEquals(1.0, StarBonus.rechargeMultiplier(0, 3, 60), 1e-9);
        assertEquals(0.7, StarBonus.rechargeMultiplier(10, 3, 60), 1e-9);
        assertEquals(0.4, StarBonus.rechargeMultiplier(20, 3, 60), 1e-9);
        assertEquals(0.4, StarBonus.rechargeMultiplier(99, 3, 60), 1e-9);
        assertEquals(0.0, StarBonus.rechargeMultiplier(99, 3, 100), 1e-9);
    }
}
