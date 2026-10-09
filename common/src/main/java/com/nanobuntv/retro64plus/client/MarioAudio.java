package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.caps.MarioCap;
import com.nanobuntv.retro64plus.config.MarioMusic;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import com.nanobuntv.retro64plus.sm64.Units;
import com.nanobuntv.retro64plus.sm64.lib.Libsm64;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.system.MemoryUtil;

/**
 * SM64's sound through Minecraft (D21, D32). libsm64 mixes everything (Mario's voice and effects, other players'
 * Marios, the optional music) into one 32 kHz stereo stream; this pulls one SM64 audio frame at a time
 * ({@code sm64_audio_tick}, 30 a second, on the render thread like every libsm64 call) and queues it on an OpenAL
 * source in Minecraft's own sound context, at the Master × Players volume.
 *
 * <p>Other players' Marios are heard from where they are: their sounds arrive with their poses and are replayed from a
 * libsm64 sound source ({@link #playRemote}) that this moves to the player's position relative to the camera every
 * frame, so SM64's own panning and distance falloff place them. Positions are scaled by {@link #DISTANCE_SCALE}: SM64
 * fades sounds out over 22000 units (200 blocks), which here is about 50 blocks.
 */
final class MarioAudio {
    /** Stereo frames libsm64 tries to keep queued (about 70 ms): enough to ride out an uneven client tick. */
    private static final int TARGET_QUEUED = 2200;
    /** Above this much queued, a frame isn't mixed at all, so a backlog drains quickly. */
    private static final int MAX_QUEUED = 3 * TARGET_QUEUED;
    private static final int MAX_CATCH_UP = 3;
    private static final float DISTANCE_SCALE = 4;
    private static final int SEQ_PLAYER_LEVEL = 0, SEQ_PLAYER_ENV = 1;
    private static final int SEQ_EVENT_CUTSCENE_COLLECT_STAR = 1;

    private record Queued(int buffer, int frames) {}

    private static int source;
    /**
     * The OpenAL context {@link #source} and the buffers belong to. Minecraft makes a new one when its sound engine
     * reloads (resource packs, F3+T, another output device); the old names mean nothing there, or name its sounds.
     */
    private static long context;
    private static final ArrayDeque<Queued> QUEUE = new ArrayDeque<>();
    private static final ArrayDeque<Integer> FREE_BUFFERS = new ArrayDeque<>();
    private static final short[] SAMPLES = new short[Libsm64.AUDIO_BUFFER_SAMPLES];
    private static ShortBuffer upload;
    private static long ticks, frames;
    private static boolean failed;
    /** Times the stream ran dry (dev diagnostics). */
    private static int underruns;
    /** The music sequence playing, or -1. */
    private static int music = -1;
    /** The caps' music playing (D17), or -1; whether it's fading out already. */
    private static int capMusic = -1;
    private static boolean capMusicFading;
    /** SM64 fades the cap music out over its last 2 seconds (decomp: fadeout_cap_music at 60 frames left). */
    private static final int CAP_FADE_TICKS = 40;

    /** The sound source kept for world sounds ({@link #playAt}); remote Marios get the others. */
    static final int WORLD_SOURCE = Libsm64.SOUND_SOURCES - 1;

    /** A remote Mario's sound source: its world position (blocks), moved into place every frame. */
    private static final Vec3[] SOURCE_POSITIONS = new Vec3[Libsm64.SOUND_SOURCES];

    private MarioAudio() {}

    /** End of every client tick. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (failed || LibSM64.status() != LibSM64.Status.READY || mc.level == null) {
            stop();
            return;
        }
        if (mc.isPaused()) return; // the queue runs dry and the source stops; it restarts on the next frame
        long current = ALC10.alcGetCurrentContext();
        if (current == 0) return; // Minecraft's sound engine is between contexts
        if (source != 0 && current != context) forgetContext();
        if (source == 0 && !open()) return;
        updateMusic();
        ticks++;
        long target = ticks * 3 / 2;
        // After a hitch (loading, a lag spike), don't make up the lost time: that would only queue sound late.
        if (target - frames > MAX_CATCH_UP) frames = target - MAX_CATCH_UP;
        while (frames < target) {
            frame(mc);
            frames++;
        }
        AL10.alSourcef(source, AL10.AL_GAIN, mc.options.getSoundSourceVolume(SoundSource.MASTER)
                * mc.options.getSoundSourceVolume(SoundSource.PLAYERS));
    }

    /** One SM64 audio frame: moves the remote sources, mixes, queues; starts the source if it ran dry. */
    private static void frame(Minecraft mc) {
        reclaim();
        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED) {
            // Ran dry: a stopped source counts every buffer queued on it as played, so new ones would be taken back
            // before they're heard. Rewinding (now with no buffers) makes it a fresh source again.
            AL10.alSourceRewind(source);
            underruns++;
        }
        moveSources(mc.gameRenderer.getMainCamera());
        int queued = queuedFrames();
        if (queued > MAX_QUEUED) return;
        int written = LibSM64.lib().sm64_audio_tick(queued, TARGET_QUEUED, SAMPLES);
        if (written <= 0) return;
        int buffer = FREE_BUFFERS.isEmpty() ? AL10.alGenBuffers() : FREE_BUFFERS.pop();
        upload.clear();
        upload.put(SAMPLES, 0, written * 2).flip();
        AL10.alBufferData(buffer, AL10.AL_FORMAT_STEREO16, upload, Libsm64.AUDIO_SAMPLE_RATE);
        AL10.alSourceQueueBuffers(source, buffer);
        QUEUE.add(new Queued(buffer, written));
        // Ran dry (a hitch, a pause, the start): play again once a little is queued.
        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && queued + written >= TARGET_QUEUED / 2) {
            AL10.alSourcePlay(source);
        }
    }

    private static boolean open() {
        try {
            source = AL10.alGenSources();
            if (AL10.alGetError() != AL10.AL_NO_ERROR || source == 0) throw new IllegalStateException("no source");
            context = ALC10.alcGetCurrentContext();
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0, 0, 0);
            upload = MemoryUtil.memAllocShort(Libsm64.AUDIO_BUFFER_SAMPLES);
            frames = 0;
            ticks = 0;
            return true;
        } catch (Throwable t) {
            // No sound is better than no game (GameDesign pillar 4).
            Retro64Plus.LOGGER.error("Couldn't open an OpenAL source for SM64's sound; Mario stays silent", t);
            failed = true;
            source = 0;
            return false;
        }
    }

    /** Stops and frees everything (left the world, or libsm64 isn't ready). */
    private static void stop() {
        if (source == 0) return;
        AL10.alSourceStop(source);
        reclaim();
        for (Queued q : QUEUE) AL10.alDeleteBuffers(q.buffer);
        QUEUE.clear();
        while (!FREE_BUFFERS.isEmpty()) AL10.alDeleteBuffers(FREE_BUFFERS.pop());
        AL10.alDeleteSources(source);
        source = 0;
        MemoryUtil.memFree(upload);
        upload = null;
        if (LibSM64.status() == LibSM64.Status.READY) {
            if (music >= 0) LibSM64.lib().sm64_stop_background_music((short) music);
            if (capMusic >= 0) LibSM64.lib().sm64_stop_background_music((short) capMusic);
        }
        music = -1;
        capMusic = -1;
        java.util.Arrays.fill(SOURCE_POSITIONS, null);
    }

    /**
     * Minecraft's sound engine made a new OpenAL context: the source and buffers went with the old one, so they're
     * dropped without a call (the music keeps playing in libsm64), and the next tick opens a new source.
     */
    private static void forgetContext() {
        QUEUE.clear();
        FREE_BUFFERS.clear();
        source = 0;
        MemoryUtil.memFree(upload);
        upload = null;
        Retro64Plus.LOGGER.info("Minecraft's sound engine restarted; SM64's sound reopens");
    }

    /** Takes back the buffers OpenAL has played. */
    private static void reclaim() {
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed && !QUEUE.isEmpty(); i++) {
            int buffer = AL10.alSourceUnqueueBuffers(source);
            QUEUE.pop();
            FREE_BUFFERS.push(buffer);
        }
    }

    /** Stereo frames queued and not yet played. */
    private static int queuedFrames() {
        int total = 0;
        for (Queued q : QUEUE) total += q.frames;
        return Math.max(0, total - AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET));
    }

    /**
     * The music option (D32): SM64's level music while you're Mario, off otherwise. Over it, the caps' music while one
     * is on (D17, whatever the option), fading out over the last cap's final 2 seconds; SM64's music queue then goes
     * back to the level music.
     */
    private static void updateMusic() {
        MarioMusic choice = Retro64PlusConfig.client().music;
        int wanted = LocalMario.isActive() && choice != null ? choice.sequence : -1;
        Libsm64 lib = LibSM64.lib();
        if (wanted != music) {
            if (music >= 0) lib.sm64_stop_background_music((short) music);
            if (wanted >= 0) lib.sm64_play_music((byte) SEQ_PLAYER_LEVEL, (short) (4 << 8 | wanted), (short) 0);
            music = wanted;
        }
        int capsWant = LocalMario.isActive() ? ClientCaps.music() : -1;
        // The shell ride's power-up music (D90), only with the music option on; it stops the moment the ride ends.
        boolean shell = capsWant < 0 && wanted >= 0 && LocalMario.ridingShell();
        int wantedCap = shell ? MarioCap.WING.music : capsWant;
        // A cap put on (again) while the music fades out: the same music starts over.
        boolean restart = capMusicFading && (shell || ClientCaps.longest() > CAP_FADE_TICKS);
        if (wantedCap != capMusic || restart) {
            if (capMusic >= 0) lib.sm64_stop_background_music((short) capMusic);
            if (wantedCap >= 0) lib.sm64_play_music((byte) SEQ_PLAYER_LEVEL, (short) (4 << 8 | wantedCap), (short) 0);
            capMusic = wantedCap;
            capMusicFading = false;
        } else if (capMusic >= 0 && !shell && !capMusicFading && ClientCaps.longest() <= CAP_FADE_TICKS) {
            lib.sm64_fadeout_background_music((short) capMusic, (short) 600);
            capMusicFading = true;
        }
    }

    /** For dev diagnostics: the stream's state. */
    static String debugState() {
        if (source == 0) return failed ? "failed" : "closed";
        int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        return String.format(java.util.Locale.ROOT, "source %d %s, %d frames queued, %d audio frames, %d underruns, music %d, cap music %d, gain %.2f",
                source, state == AL10.AL_PLAYING ? "playing" : "state 0x" + Integer.toHexString(state), queuedFrames(),
                frames, underruns, music, capMusic, AL10.alGetSourcef(source, AL10.AL_GAIN));
    }

    /** Sources for the SM64 enemies' sounds (M4), shared round the enemies by entity id; Marios use those below. */
    static final int FIRST_ENEMY_SOURCE = WORLD_SOURCE - 8;

    /** Plays an SM64 sound from an enemy (M4), through one of the enemy sources, placed where the enemy is now. */
    static void playFrom(net.minecraft.world.entity.Entity entity, int sound) {
        if (source == 0) return;
        int index = FIRST_ENEMY_SOURCE + Math.floorMod(entity.getId(), WORLD_SOURCE - FIRST_ENEMY_SOURCE);
        playRemote(index, entity.position().add(0, entity.getBbHeight() / 2, 0), java.util.List.of(sound));
    }

    /**
     * Plays one SM64 sound from {@code pos} (blocks) through {@link #WORLD_SOURCE}: things in the world that aren't a
     * Mario (a coin picked up, D108). False if SM64's sound isn't running (no ROM loaded, or it failed).
     */
    static boolean playAt(Vec3 pos, int sound) {
        if (source == 0) return false;
        playRemote(WORLD_SOURCE, pos, java.util.List.of(sound));
        return true;
    }

    /**
     * SM64's star jingle on its own (a star collected when not Mario; Mario's star dance plays it natively): the
     * collect-star sequence on SM64's environment sequence player, as the decomp's star dance does.
     */
    static void playStarJingle() {
        if (source == 0) return;
        LibSM64.lib().sm64_play_music((byte) SEQ_PLAYER_ENV, (short) (15 << 8 | SEQ_EVENT_CUTSCENE_COLLECT_STAR),
                (short) 0);
    }

    /** Replays a remote Mario's sounds from {@code sourceIndex}, at {@code pos} (blocks). */
    static void playRemote(int sourceIndex, Vec3 pos, Iterable<Integer> sounds) {
        if (source == 0 || sourceIndex < 0 || sourceIndex >= Libsm64.SOUND_SOURCES) return;
        SOURCE_POSITIONS[sourceIndex] = pos;
        moveSource(sourceIndex, Minecraft.getInstance().gameRenderer.getMainCamera());
        for (int sound : sounds) LibSM64.lib().sm64_sound_source_play(sourceIndex, sound);
    }

    /** Updates where a remote Mario's source is (blocks), between their poses. */
    static void moveRemote(int sourceIndex, Vec3 pos) {
        if (sourceIndex >= 0 && sourceIndex < Libsm64.SOUND_SOURCES) SOURCE_POSITIONS[sourceIndex] = pos;
    }

    static void releaseRemote(int sourceIndex) {
        if (sourceIndex >= 0 && sourceIndex < Libsm64.SOUND_SOURCES) SOURCE_POSITIONS[sourceIndex] = null;
    }

    private static void moveSources(Camera camera) {
        for (int i = 0; i < SOURCE_POSITIONS.length; i++) {
            if (SOURCE_POSITIONS[i] != null) moveSource(i, camera);
        }
    }

    /** Into the listener's view space (x right, y up, -z forward), in scaled SM64 units. */
    private static void moveSource(int i, Camera camera) {
        Vec3 rel = SOURCE_POSITIONS[i].subtract(camera.getPosition());
        org.joml.Vector3fc look = camera.getLookVector(), up = camera.getUpVector(), left = camera.getLeftVector();
        float scale = Units.PER_BLOCK * DISTANCE_SCALE;
        float x = (float) -(rel.x * left.x() + rel.y * left.y() + rel.z * left.z()) * scale;
        float y = (float) (rel.x * up.x() + rel.y * up.y() + rel.z * up.z()) * scale;
        float z = (float) -(rel.x * look.x() + rel.y * look.y() + rel.z * look.z()) * scale;
        LibSM64.lib().sm64_sound_source_move(i, x, y, z);
    }
}
