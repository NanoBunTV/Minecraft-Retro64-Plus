package com.nanobuntv.retro64plus.sm64.lib;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Pointer;

/**
 * JNA binding for the vendored libsm64 (native/src/libsm64.h, D7). Only functions the library really exports are
 * declared (upstream also declared two that no binary had). Not thread-safe: every call goes through
 * {@link com.nanobuntv.retro64plus.sm64.LibSM64}, which keeps them on one thread at a time.
 *
 * <p>Not declared: upstream's animation functions ({@code sm64_get_anim_info}, {@code sm64_mChar_animTick}; other
 * players are drawn with the pose functions), {@code sm64_play_sound}, whose position pointer the sound engine keeps
 * after the call (the sound source functions replace it), and {@code sm64_mChar_apply_damage} / {@code sm64_mChar_heal},
 * which change health that Minecraft owns (D36; {@link #sm64_mChar_knockback} replaces them).
 */
public interface Libsm64 extends Library {
    /**
     * The version this mod is built against; the library must report exactly this ({@code LIB_VER}). 6 is upstream's
     * 5 without native audio output, plus {@link #sm64_audio_tick}; 7 has Mario as its only model, bones, recorded
     * sounds and sound sources; 8 hands Mario's health to the mod;
     * 9 its caps; 10 carrying objects; 11 riding the Koopa shell (native/UPSTREAM.md).
     */
    int SUPPORTED_VERSION = 11;
    /** Size, in {@code int16_t}s, of the buffer {@link #sm64_audio_tick} writes to ({@code SM64_AUDIO_BUFFER_SAMPLES}). */
    int AUDIO_BUFFER_SAMPLES = 544 * 2 * 2;
    int AUDIO_SAMPLE_RATE = 32000;

    int TEXTURE_WIDTH = 64 * 11;
    int TEXTURE_HEIGHT = 64;
    int GEO_MAX_TRIANGLES = 2040;
    /** {@code SM64_MAX_BONES}: at most this many bone matrices from {@link #sm64_last_render_bones}. */
    int MAX_BONES = 32;
    /** Sound sources for {@link #sm64_sound_source_play}: 0..63. */
    int SOUND_SOURCES = 64;

    interface DebugPrint extends Callback {
        void invoke(String message);
    }

    // Global
    void sm64_global_init(Pointer rom, Pointer outTexture, DebugPrint debugPrint);

    void sm64_global_terminate();

    int sm64_get_version();

    void sm64_static_surfaces_load(SM64Surface[] surfaces, int numSurfaces);

    // Characters (upstream libsm64's "mario", renamed "mChar" in the Retro64 fork)
    int sm64_mChar_create(float x, float y, float z);

    /** A character only ever drawn with {@link #sm64_mChar_render_pose} (another player's Mario); needs no floor. */
    int sm64_mChar_create_puppet();

    void sm64_mChar_get_pose(int id, SM64MarioPose outPose);

    /** Draws a puppet in {@code pose} around the origin (plus the pose's offset); clamps every value. Model: 0. */
    void sm64_mChar_render_pose(int id, SM64MarioPose pose, int model, SM64MarioGeometryBuffers outBuffers);

    /**
     * The bones of the character drawn last ({@link #sm64_mChar_tick} or {@link #sm64_mChar_render_pose}): 16 floats
     * each (SM64's row-vector matrices: rows 0-2 the axes, row 3 the origin), in the triangles' space.
     *
     * @return bones written
     */
    int sm64_last_render_bones(float[] outMatrices, int maxBones);

    /** Sound bits Mario's own code played during the last {@link #sm64_mChar_tick}; returns how many. */
    int sm64_mChar_last_tick_sounds(int[] outSounds, int maxSounds);

    void sm64_mChar_tick(int id, SM64MarioInputs inputs, SM64MarioState outState, SM64MarioGeometryBuffers outBuffers);

    void sm64_mChar_delete(int id);

    void sm64_mChar_teleport(int id, float x, float y, float z);

    void sm64_mChar_set_velocity(int id, float x, float y, float z);

    /**
     * Minecraft owns Mario's health (D36): sets it (0x880 full, 0x100 a power meter segment, below 0x100 he dies) and
     * returns the damage SM64's own code dealt since the last call (falls, lava), in the same units.
     */
    int sm64_mChar_set_health(int id, short health);

    /**
     * The special caps Mario wears ({@code MARIO_*_CAP} flags; the mod times them, D17, D44) and those to leave out of
     * this frame's drawing (the end-of-timer flicker).
     */
    void sm64_mChar_set_caps(int id, int caps, int hiddenCaps);

    /**
     * Minecraft damaged Mario: with a source, SM64's knockback and voice away from it ({@code damage} in segments
     * picks soft, normal or hard; neither when the hit does nothing, as with the Metal or Vanish cap); without one, just
     * his voice. Health untouched; the sounds join the next frame's.
     */
    void sm64_mChar_knockback(int id, int damage, int hasSource, float xSrc, float ySrc, float zSrc);

    /** M4: picks up an object Minecraft owns (a Bob-omb) with SM64's pick-up; 1 if he started, 0 if he can't now. */
    int sm64_mChar_grab(int id);

    /** M4: 1 while Mario holds (or is picking up) the object, 0 once he threw or dropped it. */
    int sm64_mChar_is_holding(int id);

    /** M4: lets go of the held object without throwing it, into the matching empty-handed action. */
    void sm64_mChar_drop(int id);

    /** M4 checkpoint 3: Mario gets on a Koopa shell (SM64's ride), dropping what he holds; 0 if he can't (swimming). */
    int sm64_mChar_ride_shell(int id);

    /** M4 checkpoint 3: 1 while Mario rides the shell, 0 once the ride ended (a wall, Z, a hit). */
    int sm64_mChar_is_riding(int id);

    /** Overwrites the whole state flags word (caps etc.). */
    void sm64_mChar_set_state(int id, int flags);

    void sm64_mChar_set_action(int id, int actionId);

    void sm64_mChar_set_action_with_arg(int id, int actionId, int actionArg);

    void sm64_mChar_set_action_state(int id, short actionState);

    void sm64_mChar_set_water_level(int id, int yLevel);

    void sm64_mChar_set_gas_level(int id, int yLevel);

    void sm64_mChar_set_angle(int id, float angle);

    // Moving collision
    int sm64_surface_object_create(SM64SurfaceObject surfaceObject);

    void sm64_surface_object_move(int objectId, SM64ObjectTransform transform);

    void sm64_surface_object_delete(int objectId);

    // Audio (D21: the mod plays it through Minecraft's sound engine)
    /**
     * One 30 Hz audio frame: advances SM64's sound engine and writes 32 kHz stereo samples to {@code buffer}
     * ({@link #AUDIO_BUFFER_SAMPLES} long). Writes a little more while fewer than {@code numDesiredSamples} are queued.
     *
     * @return stereo frames written
     */
    int sm64_audio_tick(int numQueuedSamples, int numDesiredSamples, short[] buffer);

    void sm64_seq_player_play_sequence(byte player, byte seqId, short arg2);

    void sm64_play_music(byte player, short seqArgs, short fadeTimer);

    void sm64_stop_background_music(short seqId);

    void sm64_fadeout_background_music(short arg0, short fadeOut);

    short sm64_get_current_background_music();

    /**
     * Moves a sound source, relative to the listener in its view space (x right, y up, -z forward), in SM64 units.
     * Sounds already playing from it follow.
     */
    void sm64_sound_source_move(int source, float x, float y, float z);

    /** Plays a sound from a source; checks the bits (they may come from the network). */
    void sm64_sound_source_play(int source, int soundBits);

    void sm64_play_sound_global(int soundBits);

    int sm64_set_volume(float volume);

    // Moveset options
    void sm64_set_redive(byte enabled);

    void sm64_set_ground_pound_jump(byte enabled);
}
