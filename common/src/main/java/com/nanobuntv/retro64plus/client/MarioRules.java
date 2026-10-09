package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.MarioCharacter;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;

/**
 * The server's rules, as it last sent them: the game rules {@code retro64plusForceMario} and
 * {@code retro64plusAllowMario} (D14) and the characters it allows (D34). Defaults (not forced, allowed, every
 * character) until they arrive and after leaving a server. Enforced by {@link Retro64PlusClient}.
 */
final class MarioRules {
    private static boolean force;
    private static boolean allow = true;
    private static int allowedCharacters = MarioCharacter.ALL_BITS;

    private MarioRules() {}

    static void set(boolean forceMario, boolean allowMario, int characters) {
        force = forceMario;
        allow = allowMario;
        allowedCharacters = (characters & MarioCharacter.ALL_BITS) != 0 ? characters : MarioCharacter.MARIO.bit();
    }

    static void reset() {
        set(false, true, MarioCharacter.ALL_BITS);
    }

    static boolean force() {
        return force && allow;
    }

    static boolean allow() {
        return allow;
    }

    static boolean allowed(MarioCharacter character) {
        return (allowedCharacters & character.bit()) != 0;
    }

    /** The character to play: the chosen one (D27) if this server allows it, else the first it does. */
    static MarioCharacter character() {
        MarioCharacter chosen = Retro64PlusConfig.client().characterChoice();
        return allowed(chosen) ? chosen : MarioCharacter.byId(Integer.numberOfTrailingZeros(allowedCharacters));
    }
}
