package com.nanobuntv.retro64plus.combat;

import com.nanobuntv.retro64plus.sm64.Actions;
import org.jetbrains.annotations.Nullable;

/**
 * Mario's attacks (D29), decided by SM64's own rules ({@code determine_interaction} in the decomp's interaction.c):
 * which action and state flags hit, and in which directions. Plain Java, shared by the client (which detects hits,
 * client/MarioAttacks) and the server (which checks them, server/ServerMarioAttacks). Damage is in half-hearts, scaled
 * to vanilla weapons (a stone sword deals 5) and multiplied by the server's setting (D34).
 */
public enum MarioAttack {
    /** First and second punch. */
    PUNCH(4, 0.4f),
    /** The third hit of the punch combo, the jump kick, and riding the Koopa shell into something (D92). */
    KICK(6, 0.6f),
    /** The sweep kick (crouch + B): hits all around, at the feet. */
    TRIP(4, 0.4f),
    /** Ground pound (and twirl) coming down, and its landing. */
    GROUND_POUND(7, 0.5f),
    SLIDE_KICK(5, 0.6f),
    /** Any other attacking action at speed: the dive and its slide. */
    FAST_ATTACK(5, 0.5f),
    /** Landing on a mob's head (SM64's hit from above); Mario bounces off. */
    STOMP(5, 0.2f);

    /** {@code MarioState.flags} bits (the decomp's sm64.h). */
    public static final int MARIO_PUNCHING = 0x00100000, MARIO_KICKING = 0x00200000, MARIO_TRIPPING = 0x00400000;

    /** SM64's speed for "fast" attacks: |forward speed| of at least 26 units a frame. */
    private static final float FAST_SPEED = 26;
    /**
     * How far to the side of Mario's facing punches and kicks (90°; SM64's 60°, 0x2AAA, felt too strict in play) and the
     * sweep kick (SM64's 90°, 0x4000) reach.
     */
    private static final double PUNCH_ARC = Math.PI / 2, TRIP_ARC = Math.PI / 2;

    private static final MarioAttack[] VALUES = values();

    public final float damage;
    public final float knockback;

    MarioAttack(float damage, float knockback) {
        this.damage = damage;
        this.knockback = knockback;
    }

    /** The attack with this ordinal (from the network), or null. */
    public static @Nullable MarioAttack byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }

    /**
     * SM64's {@code determine_interaction}, for a target in contact with Mario: which attack, if any, hits it.
     *
     * @param action Mario's action
     * @param flags Mario's state flags ({@link #MARIO_PUNCHING} and so on)
     * @param faceAngle Mario's facing, radians, SM64's convention (0 faces +z, growing towards +x)
     * @param velX velocity, SM64 units per frame (x)
     * @param velY velocity (y)
     * @param velZ velocity (z)
     * @param dx target minus Mario, horizontally (any unit)
     * @param dz target minus Mario, horizontally (z)
     * @param fromAbove Mario's feet are above the target's middle (SM64: above the object's origin)
     */
    public static @Nullable MarioAttack determine(int action, int flags, double faceAngle, float velX, float velY,
            float velZ, double dx, double dz, boolean fromAbove) {
        MarioAttack attack = null;
        if (Actions.has(action, Actions.FLAG_ATTACKING)) {
            if (action == Actions.ACT_PUNCHING || action == Actions.ACT_MOVE_PUNCHING
                    || action == Actions.ACT_JUMP_KICK) {
                double yaw = Math.abs(wrap(Math.atan2(dx, dz) - faceAngle));
                if ((flags & MARIO_PUNCHING) != 0 && yaw <= PUNCH_ARC) attack = PUNCH;
                if ((flags & MARIO_KICKING) != 0 && yaw <= PUNCH_ARC) attack = KICK;
                if ((flags & MARIO_TRIPPING) != 0 && yaw <= TRIP_ARC) attack = TRIP;
            } else if (action == Actions.ACT_GROUND_POUND || action == Actions.ACT_TWIRLING) {
                if (velY < 0) attack = GROUND_POUND;
            } else if (action == Actions.ACT_GROUND_POUND_LAND || action == Actions.ACT_TWIRL_LAND) {
                // SM64 hits only on the landing's first frame; the callers hit each target once per action.
                attack = GROUND_POUND;
            } else if (action == Actions.ACT_SLIDE_KICK || action == Actions.ACT_SLIDE_KICK_SLIDE) {
                attack = SLIDE_KICK;
            } else if (Actions.has(action, Actions.FLAG_RIDING_SHELL)) {
                // SM64's shell rams at any speed; here as hard as a kick (D92).
                attack = KICK;
            } else if (Math.abs(forwardSpeed(faceAngle, velX, velZ)) >= FAST_SPEED) {
                attack = FAST_ATTACK;
            }
        }
        // SM64's hit from above, the stomp (its hit from below hurts Mario, so it isn't one of his attacks).
        if (attack == null && Actions.has(action, Actions.FLAG_AIR) && velY < 0 && fromAbove) attack = STOMP;
        return attack;
    }

    /** Whether a Mario in {@code action} could have made this attack (the server's check of a client's report). */
    public boolean possibleIn(int action) {
        return switch (this) {
            case PUNCH, TRIP -> action == Actions.ACT_PUNCHING || action == Actions.ACT_MOVE_PUNCHING
                    || action == Actions.ACT_JUMP_KICK;
            case KICK -> action == Actions.ACT_PUNCHING || action == Actions.ACT_MOVE_PUNCHING
                    || action == Actions.ACT_JUMP_KICK || Actions.has(action, Actions.FLAG_RIDING_SHELL);
            case GROUND_POUND -> action == Actions.ACT_GROUND_POUND || action == Actions.ACT_GROUND_POUND_LAND
                    || action == Actions.ACT_TWIRLING || action == Actions.ACT_TWIRL_LAND;
            case SLIDE_KICK -> action == Actions.ACT_SLIDE_KICK || action == Actions.ACT_SLIDE_KICK_SLIDE;
            case FAST_ATTACK -> Actions.has(action, Actions.FLAG_ATTACKING);
            case STOMP -> Actions.has(action, Actions.FLAG_AIR);
        };
    }

    /**
     * How far beyond touching (blocks) this attack reaches; SM64's objects are bigger than most mobs, and half a block
     * for punches felt too strict in play.
     */
    public double reach() {
        return switch (this) {
            case PUNCH, KICK, TRIP -> 1.0;
            case STOMP -> 0;
            default -> 0.2;
        };
    }

    /** SM64's forward velocity: the horizontal velocity along Mario's facing. */
    static double forwardSpeed(double faceAngle, float velX, float velZ) {
        return velX * Math.sin(faceAngle) + velZ * Math.cos(faceAngle);
    }

    /** An angle wrapped to -π..π. */
    private static double wrap(double angle) {
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }
}
