package com.nanobuntv.retro64plus.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nanobuntv.retro64plus.sm64.Actions;
import org.junit.jupiter.api.Test;

/** SM64's determine_interaction as ported for D29: Mario faces +z (angle 0) unless said otherwise. */
class MarioAttackTest {
    @Test
    void punchesHitInFrontOnly() {
        int punching = MarioAttack.MARIO_PUNCHING;
        assertEquals(MarioAttack.PUNCH, hit(Actions.ACT_PUNCHING, punching, 0, 0, 0, 0, 1, false));
        // 80° to the side still hits (90° each way; SM64's 60° felt too strict), 100° doesn't, behind doesn't.
        assertEquals(MarioAttack.PUNCH, hit(Actions.ACT_PUNCHING, punching, 0, 0, 0, Math.sin(Math.toRadians(80)),
                Math.cos(Math.toRadians(80)), false));
        assertNull(hit(Actions.ACT_PUNCHING, punching, 0, 0, 0, Math.sin(Math.toRadians(100)),
                Math.cos(Math.toRadians(100)), false));
        assertNull(hit(Actions.ACT_PUNCHING, punching, 0, 0, 0, 0, -1, false));
        // Facing -z (180°), a target at -z is in front; across the ±180° wrap.
        assertEquals(MarioAttack.PUNCH, hit(Actions.ACT_PUNCHING, punching, Math.PI, 0, 0, 0.1, -1, false));
        // Between the punch's active frames (no flag) nothing hits.
        assertNull(hit(Actions.ACT_PUNCHING, 0, 0, 0, 0, 0, 1, false));
    }

    @Test
    void kicksAndTheSweepKick() {
        assertEquals(MarioAttack.KICK, hit(Actions.ACT_JUMP_KICK, MarioAttack.MARIO_KICKING, 0, 0, 0, 0, 1, false));
        // The sweep kick reaches 90° each way.
        assertEquals(MarioAttack.TRIP, hit(Actions.ACT_PUNCHING, MarioAttack.MARIO_TRIPPING, 0, 0, 0, 1, 0.01, false));
    }

    @Test
    void groundPoundOnlyComingDown() {
        assertEquals(MarioAttack.GROUND_POUND, hit(Actions.ACT_GROUND_POUND, 0, 0, 0, -50, 1, 0, true));
        assertEquals(MarioAttack.GROUND_POUND, hit(Actions.ACT_GROUND_POUND_LAND, 0, 0, 0, 0, 1, 0, false));
        // The wind-up (not falling yet) hits nothing, not even as a stomp.
        assertNull(hit(Actions.ACT_GROUND_POUND, 0, 0, 0, 5, 1, 0, true));
    }

    @Test
    void diveAtSpeedButNotSlow() {
        float fast = 32;
        assertEquals(MarioAttack.FAST_ATTACK, hit(Actions.ACT_DIVE, 0, 0, 0, 0, 0, 1, false, fast));
        assertEquals(MarioAttack.SLIDE_KICK, hit(Actions.ACT_SLIDE_KICK, 0, 0, 0, 0, 0, 1, false));
        assertNull(hit(Actions.ACT_DIVE, 0, 0, 0, 0, 0, 1, false, 10));
    }

    @Test
    void stompNeedsFallingFromAbove() {
        assertEquals(MarioAttack.STOMP, hit(Actions.ACT_FREEFALL, 0, 0, 0, -20, 0, 0, true));
        assertNull(hit(Actions.ACT_FREEFALL, 0, 0, 0, -20, 0, 0, false));
        assertNull(hit(Actions.ACT_JUMP, 0, 0, 0, 20, 0, 0, true)); // rising
        assertNull(hit(Actions.ACT_WALKING, 0, 0, 0, 0, 0, 1, true)); // on the ground
    }

    @Test
    void serverChecksTheAction() {
        assertTrue(MarioAttack.PUNCH.possibleIn(Actions.ACT_MOVE_PUNCHING));
        assertFalse(MarioAttack.PUNCH.possibleIn(Actions.ACT_WALKING));
        assertTrue(MarioAttack.STOMP.possibleIn(Actions.ACT_FREEFALL));
        assertFalse(MarioAttack.STOMP.possibleIn(Actions.ACT_IDLE));
        assertTrue(MarioAttack.FAST_ATTACK.possibleIn(Actions.ACT_DIVE));
        assertNull(MarioAttack.byId(99));
    }

    private static MarioAttack hit(int action, int flags, double face, float vx, float vy, double dx, double dz,
            boolean above) {
        return MarioAttack.determine(action, flags, face, vx, vy, 0, dx, dz, above);
    }

    /** With forward speed along the facing (+z for face 0). */
    private static MarioAttack hit(int action, int flags, double face, float vx, float vy, double dx, double dz,
            boolean above, float forward) {
        return MarioAttack.determine(action, flags, face, vx, vy, forward, dx, dz, above);
    }
}
