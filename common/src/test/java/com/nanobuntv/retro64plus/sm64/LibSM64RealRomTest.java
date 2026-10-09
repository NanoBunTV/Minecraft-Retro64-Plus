package com.nanobuntv.retro64plus.sm64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nanobuntv.retro64plus.sm64.lib.Libsm64;
import com.nanobuntv.retro64plus.sm64.lib.SM64MarioGeometryBuffers;
import com.nanobuntv.retro64plus.sm64.lib.SM64MarioInputs;
import com.nanobuntv.retro64plus.sm64.lib.SM64MarioPose;
import com.nanobuntv.retro64plus.sm64.lib.SM64MarioState;
import com.nanobuntv.retro64plus.sm64.lib.SM64Surface;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

/**
 * The real native library on a real ROM, outside Minecraft: extraction from the (test) jar layout, the version check,
 * {@code sm64_global_init}, the texture atlas, and a Mario standing, walking and jumping on a floor, which exercises
 * every struct the tick uses. Runs only with {@code RETRO64_TEST_ROM} set (see common/build.gradle.kts).
 */
@EnabledIfSystemProperty(named = "retro64plus.testRom", matches = ".+")
class LibSM64RealRomTest {
    /** SM64 units per Minecraft block (upstream's SCALE_FACTOR). */
    private static final int UNITS = 110;

    /** Never deleted: Windows keeps the loaded library locked until the JVM exits. */
    @TempDir(cleanup = CleanupMode.NEVER)
    Path modDir;

    @Test
    void initsAndRunsMario() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        LibSM64.start(rom, modDir).get(60, TimeUnit.SECONDS);
        assertEquals(LibSM64.Status.READY, LibSM64.status());
        // The library keeps running its own threads after init (upstream's audio thread); the process must survive.
        Thread.sleep(5000);

        // The atlas came from the ROM: a good share of it is opaque.
        byte[] texture = LibSM64.texture();
        int opaque = 0;
        for (int i = 3; i < texture.length; i += 4) if (texture[i] != 0) opaque++;
        assertTrue(opaque > 1000, "only " + opaque + " opaque texels");

        Libsm64 lib = LibSM64.lib();
        lib.sm64_static_surfaces_load(floor(), 2);
        int id = lib.sm64_mChar_create(0, 3 * UNITS, 0);
        assertTrue(id >= 0, "create returned " + id);

        SM64MarioInputs inputs = new SM64MarioInputs();
        inputs.camLookX = 0;
        inputs.camLookZ = 1;
        SM64MarioState state = new SM64MarioState();
        SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();

        // Falls three blocks and lands on the floor at y = 0.
        for (int i = 0; i < 60; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertEquals(0, state.position[1], 1, "should stand on the floor");
        assertEquals(8, state.health >> 8, "full power meter");
        assertTrue(buffers.triangles() > 100, "drew " + buffers.triangles() + " triangles");

        // Walks: stick held, no frame limiter (native/UPSTREAM.md), so 30 calls are 30 frames.
        float startX = state.position[0], startZ = state.position[2];
        inputs.stickY = -1;
        for (int i = 0; i < 30; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        double moved = Math.hypot(state.position[0] - startX, state.position[2] - startZ);
        assertTrue(moved > UNITS, "moved only " + moved + " units");

        // Jumps.
        inputs.stickY = 0;
        inputs.buttonA = 1;
        float highest = 0;
        short[] audio = new short[Libsm64.AUDIO_BUFFER_SAMPLES];
        int loudest = 0;
        for (int i = 0; i < 10; i++) {
            lib.sm64_mChar_tick(id, inputs, state, buffers);
            highest = Math.max(highest, state.position[1]);
            // D21: audio is pulled on the same thread, one frame per tick; the jump makes a sound.
            int frames = lib.sm64_audio_tick(0, 1100, audio);
            assertTrue(frames > 0 && frames * 2 <= audio.length, "audio frames " + frames);
            for (int s = 0; s < frames * 2; s++) loudest = Math.max(loudest, Math.abs(audio[s]));
        }
        assertTrue(highest > UNITS / 2f, "jumped only " + highest + " units");
        assertTrue(loudest > 100, "audio stayed silent (peak " + loudest + ")");

        lib.sm64_mChar_delete(id);
    }

    /**
     * The control conventions client/LocalMario uses: for a player facing yaw 0 (towards +z), camLook is the backward
     * vector (sin yaw, -cos yaw) = (0, -1); stickY = forward moves Mario towards +z, stickX = -right towards -x (a
     * player facing +z has -x on their right).
     */
    @Test
    void stickFollowsTheCamera() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        LibSM64.start(rom, modDir).get(60, TimeUnit.SECONDS);
        Libsm64 lib = LibSM64.lib();
        lib.sm64_static_surfaces_load(floor(), 2);

        float[] forward = walk(lib, 1, 0);
        assertTrue(forward[1] > UNITS && Math.abs(forward[0]) < forward[1] / 4, "forward went " + forward[0] + ", " + forward[1]);
        float[] right = walk(lib, 0, 1);
        assertTrue(right[0] < -UNITS && Math.abs(right[1]) < -right[0] / 4, "right went " + right[0] + ", " + right[1]);
    }

    /**
     * The calls client/LocalMario makes, at a real world position (the dev world's spawn, z = -382 blocks: beyond
     * SM64's original ±32768-unit range).
     */
    @Test
    void runsLikeTheGameDoes() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        LibSM64.start(rom, modDir).get(60, TimeUnit.SECONDS);
        Libsm64 lib = LibSM64.lib();
        int x = Math.round(20.7f * UNITS), y = 78 * UNITS, z = -382 * UNITS;
        SM64Surface[] surfaces = floor();
        for (SM64Surface s : surfaces) {
            for (int v = 0; v < 3; v++) {
                s.vertices[v * 3] += x;
                s.vertices[v * 3 + 1] += y;
                s.vertices[v * 3 + 2] += z;
            }
        }
        lib.sm64_static_surfaces_load(surfaces, 2);
        int id = lib.sm64_mChar_create(x, y, z);
        assertTrue(id >= 0, "create returned " + id);
        lib.sm64_mChar_set_angle(id, Units.nativeAngle(Math.toRadians(-90)));
        SM64MarioInputs inputs = new SM64MarioInputs();
        SM64MarioState state = new SM64MarioState();
        SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();
        for (int i = 0; i < 30; i++) {
            lib.sm64_mChar_set_water_level(id, -10_000_000);
            lib.sm64_mChar_set_gas_level(id, -10_000_000);
            lib.sm64_mChar_tick(id, inputs, state, buffers);
        }
        assertEquals(y, state.position[1], 1);

        // The game reloads the collision as Mario moves. Each load frees the previous list (native/UPSTREAM.md:
        // characters' floor pointers are looked up again); alternate sizes so the new list lands elsewhere.
        for (int i = 0; i < 60; i++) {
            SM64Surface[] bigger = (SM64Surface[]) new SM64Surface().toArray(2 + (i % 2) * 500);
            for (int t = 0; t < 2; t++) System.arraycopy(surfaces[t].vertices, 0, bigger[t].vertices, 0, 9);
            for (int t = 2; t < bigger.length; t++) {
                int[] far = {0, 0, 0, UNITS, 0, 0, 0, 0, UNITS}; // small floors far away
                for (int c = 0; c < 9; c += 3) {
                    bigger[t].vertices[c] = far[c] + 200 * UNITS + t;
                    bigger[t].vertices[c + 1] = far[c + 1];
                    bigger[t].vertices[c + 2] = far[c + 2];
                }
            }
            lib.sm64_static_surfaces_load(bigger, bigger.length);
            lib.sm64_mChar_tick(id, inputs, state, buffers);
        }
        assertEquals(y, state.position[1], 1, "still standing after 60 collision reloads");
        lib.sm64_mChar_delete(id);
    }

    /**
     * Off a cliff whose bottom is outside the collision box, the only floor below Mario is the safety floor 70 blocks
     * under the world (CollisionBuilder), beneath SM64's old FLOOR_LOWER_LIMIT: he must walk off the edge and fall,
     * not bonk off an invisible out-of-bounds wall (native/UPSTREAM.md).
     */
    @Test
    void walksOffACliffOntoADeepFloor() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        LibSM64.start(rom, modDir).get(60, TimeUnit.SECONDS);
        Libsm64 lib = LibSM64.lib();
        int r = 2 * UNITS, deep = -134 * UNITS, far = 64 * UNITS;
        int[][] tris = {
                {-r, 0, -r, -r, 0, r, r, 0, -r}, {r, 0, -r, -r, 0, r, r, 0, r}, // a 4×4-block platform at y = 0
                {-far, deep, -far, -far, deep, far, far, deep, -far}, {far, deep, -far, -far, deep, far, far, deep, far},
        };
        SM64Surface[] surfaces = (SM64Surface[]) new SM64Surface().toArray(tris.length);
        for (int i = 0; i < tris.length; i++) System.arraycopy(tris[i], 0, surfaces[i].vertices, 0, 9);
        lib.sm64_static_surfaces_load(surfaces, surfaces.length);
        int id = lib.sm64_mChar_create(0, 0, 0);
        SM64MarioInputs inputs = new SM64MarioInputs();
        inputs.camLookZ = -1; // facing +z
        SM64MarioState state = new SM64MarioState();
        SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();
        inputs.stickY = 1;
        for (int i = 0; i < 60; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertTrue(state.position[2] > r + UNITS, "stopped at z " + state.position[2] + " (edge at " + r + ")");
        assertTrue(state.position[1] < -UNITS, "didn't fall: y " + state.position[1]);
        lib.sm64_mChar_delete(id);
    }

    /**
     * Another player's Mario (M1 checkpoint 3): a walking Mario's pose, drawn on a puppet, gives the same triangles
     * around the origin; and garbage poses (what a malicious client could send) must not crash anything.
     */
    @Test
    void puppetShowsAPoseAndSurvivesGarbage() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        LibSM64.start(rom, modDir).get(60, TimeUnit.SECONDS);
        Libsm64 lib = LibSM64.lib();
        lib.sm64_static_surfaces_load(floor(), 2);
        int id = lib.sm64_mChar_create(3 * UNITS, 0, 2 * UNITS); // away from the origin, to show the pose is relative
        SM64MarioInputs inputs = new SM64MarioInputs();
        inputs.camLookZ = -1;
        inputs.stickY = 1;
        SM64MarioState state = new SM64MarioState();
        SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();
        for (int i = 0; i < 20; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        SM64MarioPose pose = new SM64MarioPose();
        lib.sm64_mChar_get_pose(id, pose);
        assertTrue(pose.animID >= 0, "anim " + pose.animID);

        int puppet = lib.sm64_mChar_create_puppet();
        assertTrue(puppet >= 0, "puppet " + puppet);
        SM64MarioGeometryBuffers puppetBuffers = new SM64MarioGeometryBuffers();
        lib.sm64_mChar_render_pose(puppet, pose, 0, puppetBuffers);
        assertEquals(buffers.triangles(), puppetBuffers.triangles(), "same model, same triangles");
        float[] positions = puppetBuffers.position.getFloatArray(0, puppetBuffers.triangles() * 9);
        float maxAbs = 0;
        for (int i = 0; i < positions.length; i += 3) {
            maxAbs = Math.max(maxAbs, Math.max(Math.abs(positions[i]), Math.abs(positions[i + 2])));
        }
        assertTrue(maxAbs < UNITS, "puppet drawn around the origin, but reaches " + maxAbs);

        java.util.Random random = new java.util.Random(64);
        for (int i = 0; i < 5000; i++) {
            SM64MarioPose junk = new SM64MarioPose();
            for (int c = 0; c < 3; c++) {
                junk.offset[c] = random.nextInt(3) == 0 ? Float.NaN : (random.nextFloat() - 0.5f) * 1e6f;
                junk.angle[c] = (short) random.nextInt();
                junk.torsoAngle[c] = (short) random.nextInt();
                junk.headAngle[c] = (short) random.nextInt();
            }
            junk.action = random.nextInt();
            junk.animID = (short) random.nextInt();
            junk.animYTrans = (short) random.nextInt();
            junk.animFrame = (short) random.nextInt();
            junk.modelState = (short) random.nextInt();
            junk.capState = (byte) random.nextInt();
            junk.eyeState = (byte) random.nextInt();
            junk.handState = (byte) random.nextInt();
            junk.wingFlutter = (byte) random.nextInt();
            junk.grabPos = (byte) random.nextInt();
            junk.punchState = (byte) random.nextInt();
            lib.sm64_mChar_render_pose(puppet, junk, random.nextInt(), puppetBuffers);
        }
        lib.sm64_mChar_delete(puppet);
        lib.sm64_mChar_delete(id);
    }

    /**
     * The skin character's skeleton (D28): Mario standing has 20 bones in the order client/SkinBody expects (head above
     * the torso above the pelvis, the left limbs on Mario's left, feet at the bottom, each limb bone's x axis pointing
     * at the next joint). And the sounds a tick records for other players (D32): a jump makes some.
     */
    @Test
    void reportsBonesAndSounds() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        LibSM64.start(rom, modDir).get(60, TimeUnit.SECONDS);
        Libsm64 lib = LibSM64.lib();
        lib.sm64_static_surfaces_load(floor(), 2);
        int id = lib.sm64_mChar_create(0, 0, 0); // faces +z, so his left is +x
        SM64MarioInputs inputs = new SM64MarioInputs();
        inputs.camLookZ = -1;
        SM64MarioState state = new SM64MarioState();
        SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();
        for (int i = 0; i < 60; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        float[] bones = new float[Libsm64.MAX_BONES * 16];
        int count = lib.sm64_last_render_bones(bones, Libsm64.MAX_BONES);
        assertEquals(20, count, "bones");
        float headY = bones[3 * 16 + 13], torsoY = bones[2 * 16 + 13], pelvisY = bones[16 + 13];
        assertTrue(headY > torsoY && torsoY > pelvisY, "head " + headY + " torso " + torsoY + " pelvis " + pelvisY);
        assertTrue(bones[5 * 16 + 12] > 10 && bones[9 * 16 + 12] < -10, "left arm at +x, right arm at -x");
        assertTrue(bones[13 * 16 + 12] > 5 && bones[17 * 16 + 12] < -5, "left leg at +x, right leg at -x");
        assertTrue(bones[15 * 16 + 13] < 20 && bones[19 * 16 + 13] < 20, "feet near the floor");
        // The upper arm's x axis points from the shoulder to the elbow.
        float ex = bones[6 * 16 + 12] - bones[5 * 16 + 12], ey = bones[6 * 16 + 13] - bones[5 * 16 + 13],
                ez = bones[6 * 16 + 14] - bones[5 * 16 + 14];
        float dot = ex * bones[5 * 16] + ey * bones[5 * 16 + 1] + ez * bones[5 * 16 + 2];
        assertTrue(dot > 0.9 * Math.sqrt(ex * ex + ey * ey + ez * ez) * 0.25, "upper arm x axis along the arm");

        inputs.buttonA = 1;
        int[] sounds = new int[16];
        int heard = 0;
        for (int i = 0; i < 5; i++) {
            lib.sm64_mChar_tick(id, inputs, state, buffers);
            heard += lib.sm64_mChar_last_tick_sounds(sounds, sounds.length);
        }
        assertTrue(heard > 0, "a jump makes sounds");
        // Replaying them from a sound source, and garbage bits (a bad bank) from the network, mustn't crash.
        lib.sm64_sound_source_move(3, 200, 0, -500);
        lib.sm64_sound_source_play(3, sounds[0]);
        lib.sm64_sound_source_play(3, 0xF0000000);
        lib.sm64_sound_source_play(99, sounds[0]);
        lib.sm64_sound_source_move(3, Float.NaN, Float.POSITIVE_INFINITY, 0);
        short[] audio = new short[Libsm64.AUDIO_BUFFER_SAMPLES];
        for (int i = 0; i < 10; i++) lib.sm64_audio_tick(0, 1100, audio);
        lib.sm64_mChar_delete(id);
    }

    /**
     * M3 (D63): the star dance plays "Here we go!" and ends with Mario back on his feet (the fork's handler was all
     * commented out, so it never ended), facing the camera as in SM64.
     */
    @Test
    void starDanceEnds() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        LibSM64.start(rom, modDir).get(60, TimeUnit.SECONDS);
        Libsm64 lib = LibSM64.lib();
        lib.sm64_static_surfaces_load(floor(), 2);
        int id = lib.sm64_mChar_create(0, 0, 0);
        SM64MarioInputs inputs = new SM64MarioInputs();
        inputs.camLookX = 1; // the camera sits at +x
        SM64MarioState state = new SM64MarioState();
        SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();
        for (int i = 0; i < 30; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        int[] sounds = new int[16];
        lib.sm64_mChar_last_tick_sounds(sounds, sounds.length);
        lib.sm64_mChar_set_action_with_arg(id, Actions.ACT_STAR_DANCE_NO_EXIT, 1);
        boolean hereWeGo = false, danced = false;
        int frames = 0;
        for (; frames < 300; frames++) {
            lib.sm64_mChar_tick(id, inputs, state, buffers);
            int n = lib.sm64_mChar_last_tick_sounds(sounds, sounds.length);
            for (int i = 0; i < n; i++) hereWeGo |= (sounds[i] & 0xFFFF0000) == 0x240C0000;
            if (state.action == Actions.ACT_STAR_DANCE_NO_EXIT) {
                danced = true;
                assertTrue(Math.sin(state.faceAngle) > 0.99, "faces the camera (+x): " + state.faceAngle);
            } else {
                break;
            }
        }
        assertTrue(danced, "never danced");
        assertTrue(hereWeGo, "no \"Here we go!\"");
        assertEquals(Actions.ACT_IDLE, state.action, "after the dance (frame " + frames + ")");
        assertTrue(frames > 80 && frames < 200, "dance length " + frames);
        lib.sm64_mChar_delete(id);
    }

    /**
     * M4: Mario picks up an object Minecraft owns (a Bob-omb) with SM64's own pick-up, holds it, and throws it with B;
     * in the air he can't start a pick-up; dropping lets go into an empty-handed action.
     */
    @Test
    void grabsHoldsAndThrows() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        LibSM64.start(rom, modDir).get(60, TimeUnit.SECONDS);
        Libsm64 lib = LibSM64.lib();
        lib.sm64_static_surfaces_load(floor(), 2);
        int id = lib.sm64_mChar_create(0, 0, 0);
        SM64MarioInputs inputs = new SM64MarioInputs();
        inputs.camLookZ = 1;
        SM64MarioState state = new SM64MarioState();
        SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();
        for (int i = 0; i < 30; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertEquals(1, lib.sm64_mChar_grab(id), "grab refused while standing");
        assertEquals(0, lib.sm64_mChar_grab(id), "a second grab while picking up");
        for (int i = 0; i < 40; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertEquals(1, lib.sm64_mChar_is_holding(id));
        assertEquals(Actions.ACT_HOLD_IDLE, state.action, "holding");
        // B throws: SM64 lets go on the throw's 7th frame.
        inputs.buttonB = 1;
        lib.sm64_mChar_tick(id, inputs, state, buffers);
        inputs.buttonB = 0;
        boolean threw = (state.action & Actions.FLAG_THROWING) != 0;
        for (int i = 0; i < 10 && lib.sm64_mChar_is_holding(id) != 0; i++) {
            lib.sm64_mChar_tick(id, inputs, state, buffers);
            threw |= (state.action & Actions.FLAG_THROWING) != 0;
        }
        assertTrue(threw, "never threw");
        assertEquals(0, lib.sm64_mChar_is_holding(id), "still holding after the throw");
        // Drop: picked up again, then let go by the mod.
        for (int i = 0; i < 30; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertEquals(1, lib.sm64_mChar_grab(id));
        for (int i = 0; i < 40; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        lib.sm64_mChar_drop(id);
        lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertEquals(0, lib.sm64_mChar_is_holding(id));
        assertEquals(Actions.ACT_IDLE, state.action, "after the drop");
        // No pick-up in the air.
        inputs.buttonA = 1;
        for (int i = 0; i < 4; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertTrue(Actions.has(state.action, Actions.FLAG_AIR), "jumping");
        assertEquals(0, lib.sm64_mChar_grab(id), "grabbed in the air");
        lib.sm64_mChar_delete(id);
    }

    /**
     * M4 checkpoint 3 (D88, D89): Mario gets on a Koopa shell (dropping what he holds), rides it forward fast, and Z
     * ends the ride into a crouch slide; a ride the mod ends with another action is over too.
     */
    @Test
    void ridesTheShell() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        LibSM64.start(rom, modDir).get(60, TimeUnit.SECONDS);
        Libsm64 lib = LibSM64.lib();
        lib.sm64_static_surfaces_load(floor(), 2);
        int id = lib.sm64_mChar_create(0, 0, 0);
        SM64MarioInputs inputs = new SM64MarioInputs();
        inputs.camLookZ = 1;
        SM64MarioState state = new SM64MarioState();
        SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();
        for (int i = 0; i < 30; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertEquals(1, lib.sm64_mChar_grab(id));
        for (int i = 0; i < 40; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertEquals(1, lib.sm64_mChar_ride_shell(id), "ride refused");
        assertEquals(0, lib.sm64_mChar_ride_shell(id), "a second ride while riding");
        lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertEquals(0, lib.sm64_mChar_is_holding(id), "still holding on the shell");
        assertEquals(Actions.ACT_RIDING_SHELL_GROUND, state.action);
        float startZ = state.position[2];
        inputs.stickY = 1;
        // 40 frames: still on the test floor (1100 units from the middle), speeding up slowly (update_shell_speed).
        for (int i = 0; i < 40; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertEquals(1, lib.sm64_mChar_is_riding(id), "the ride ended");
        assertTrue(Actions.has(state.action, Actions.FLAG_RIDING_SHELL), "riding");
        assertTrue(Math.abs(state.position[2] - startZ) > 450, "rode only " + (state.position[2] - startZ));
        inputs.stickY = 0;
        inputs.buttonZ = 1;
        lib.sm64_mChar_tick(id, inputs, state, buffers);
        inputs.buttonZ = 0;
        assertEquals(0, lib.sm64_mChar_is_riding(id), "still riding after Z");
        assertEquals(Actions.ACT_CROUCH_SLIDE, state.action);
        for (int i = 0; i < 40; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertEquals(1, lib.sm64_mChar_ride_shell(id));
        lib.sm64_mChar_tick(id, inputs, state, buffers);
        lib.sm64_mChar_set_action(id, Actions.ACT_IDLE);
        assertEquals(0, lib.sm64_mChar_is_riding(id), "a ride left by another action");
        lib.sm64_mChar_delete(id);
    }

    /** Walks a fresh Mario for 30 frames from the origin; returns how far he went (x, z). */
    /**
     * M2 (D36, D37): Minecraft owns health. SM64 no longer changes it itself; its own damage (a long fall) comes back
     * from {@code sm64_mChar_set_health}; Minecraft's hits knock Mario back without touching health; a dead player's
     * health makes SM64 play its death.
     */
    @Test
    void healthBelongsToTheMod() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        LibSM64.start(rom, modDir).get(60, TimeUnit.SECONDS);
        Libsm64 lib = LibSM64.lib();
        lib.sm64_static_surfaces_load(floor(), 2);
        int id = lib.sm64_mChar_create(0, 20 * UNITS, 0); // 2200 units up: a damaging fall (over 1150)
        lib.sm64_mChar_set_action(id, Actions.ACT_FREEFALL); // as off a ledge (the spawn action never hurts)
        SM64MarioInputs inputs = new SM64MarioInputs();
        inputs.camLookZ = 1;
        SM64MarioState state = new SM64MarioState();
        SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();
        int hurt = 0;
        for (int i = 0; i < 90; i++) {
            hurt += lib.sm64_mChar_set_health(id, (short) 0x880);
            lib.sm64_mChar_tick(id, inputs, state, buffers);
        }
        assertEquals(0, state.position[1], 1, "landed");
        assertEquals(8 * 0x40, hurt, "SM64's fall damage (8 hurt steps with the cap on)");
        assertEquals(0x880, state.health, "health untouched by SM64");

        // Health set stays set: no drain or healing of SM64's own.
        lib.sm64_mChar_set_health(id, (short) 0x380);
        for (int i = 0; i < 30; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertEquals(0x380, state.health);

        // A hit from in front: knocked back, voice recorded for other players, health the same.
        int[] sounds = new int[16];
        lib.sm64_mChar_last_tick_sounds(sounds, sounds.length); // drop the landing's
        lib.sm64_mChar_knockback(id, 2, 1, 0, 0, 2 * UNITS);
        lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertTrue(state.action == Actions.ACT_BACKWARD_GROUND_KB || state.action == Actions.ACT_FORWARD_GROUND_KB,
                "knockback action 0x" + Integer.toHexString(state.action));
        assertTrue(lib.sm64_mChar_last_tick_sounds(sounds, sounds.length) > 0, "the hit's voice was recorded");
        assertEquals(0x380, state.health);
        assertEquals(0, lib.sm64_mChar_set_health(id, (short) 0x380), "a knockback isn't SM64 damage");

        // Dead in Minecraft: SM64's standing death once he's back on his feet.
        boolean died = false;
        for (int i = 0; i < 120 && !died; i++) {
            lib.sm64_mChar_set_health(id, (short) 0xFF);
            lib.sm64_mChar_tick(id, inputs, state, buffers);
            died = state.action == Actions.ACT_STANDING_DEATH;
        }
        assertTrue(died, "no death action (0x" + Integer.toHexString(state.action) + ")");
        lib.sm64_mChar_delete(id);
    }

    /**
     * Caps (M2 checkpoint 2, D17, D43): the mod sets them each frame. The Vanish cap walks through SM64's vanish cap
     * walls (which libsm64 had dropped); Metal draws with the metal texture (atlas slot 0); a flickering cap is only
     * left out of the drawing, its physics stay.
     */
    @Test
    void capsWorkAsTheModSetsThem() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        LibSM64.start(rom, modDir).get(60, TimeUnit.SECONDS);
        Libsm64 lib = LibSM64.lib();
        int r = 6 * UNITS, wz = 2 * UNITS, top = 3 * UNITS;
        int[][] tris = {
                {-r, 0, -r, -r, 0, r, r, 0, -r}, {r, 0, -r, -r, 0, r, r, 0, r}, // floor
                // A vanish cap wall across z = 2 blocks, facing Mario (normal -z).
                {-r, 0, wz, r, top, wz, r, 0, wz}, {-r, 0, wz, -r, top, wz, r, top, wz},
        };
        SM64Surface[] surfaces = (SM64Surface[]) new SM64Surface().toArray(tris.length);
        for (int i = 0; i < tris.length; i++) {
            System.arraycopy(tris[i], 0, surfaces[i].vertices, 0, 9);
            if (i >= 2) surfaces[i].type = Surfaces.VANISH_CAP_WALLS;
        }
        lib.sm64_static_surfaces_load(surfaces, surfaces.length);
        SM64MarioInputs inputs = new SM64MarioInputs();
        inputs.camLookZ = -1; // facing +z, towards the wall
        SM64MarioState state = new SM64MarioState();
        SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();

        float[] reached = new float[2];
        for (int vanish = 0; vanish < 2; vanish++) {
            int id = lib.sm64_mChar_create(0, 0, 0);
            inputs.stickY = 1;
            for (int i = 0; i < 60; i++) {
                lib.sm64_mChar_set_caps(id, vanish == 1 ? 0x02 : 0, 0);
                lib.sm64_mChar_tick(id, inputs, state, buffers);
            }
            reached[vanish] = state.position[2];
            lib.sm64_mChar_delete(id);
        }
        assertTrue(reached[0] < wz, "walked through the wall without the cap: z " + reached[0]);
        assertTrue(reached[1] > wz + UNITS, "the Vanish cap didn't pass the wall: z " + reached[1]);

        // Metal: the metal texture; flickering off: drawn plain but still metal.
        inputs.stickY = 0;
        int id = lib.sm64_mChar_create(0, 0, -3 * UNITS);
        for (int i = 0; i < 3; i++) {
            lib.sm64_mChar_set_caps(id, 0x04, 0);
            lib.sm64_mChar_tick(id, inputs, state, buffers);
        }
        assertEquals(0x04, state.flags & 0x0E, "metal flag");
        assertEquals(buffers.triangles(), metalTriangles(buffers), "all metal (the fork had disabled the metal model)");
        lib.sm64_mChar_set_caps(id, 0x04, 0x04);
        lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertEquals(0x04, state.flags & 0x0E, "still metal while flickering");
        assertTrue(metalTriangles(buffers) < 10, "flickering: drawn without the metal (" + metalTriangles(buffers) + ")");
        lib.sm64_mChar_set_caps(id, 0, 0);
        lib.sm64_mChar_tick(id, inputs, state, buffers);
        assertEquals(0, state.flags & 0x0E, "caps off");
        lib.sm64_mChar_delete(id);
    }

    /**
     * The Vanish cap passes a pane-like block: a thin box whose six faces are all vanish cap walls, as CollisionBuilder
     * makes them for panes, bars and fences. Its underside lies at floor height, where SM64's ceiling check must not
     * stop him; its top stays a floor.
     */
    @Test
    void vanishPassesAThinBlock() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        LibSM64.start(rom, modDir).get(60, TimeUnit.SECONDS);
        Libsm64 lib = LibSM64.lib();
        int r = 6 * UNITS, z0 = 2 * UNITS - 8, z1 = 2 * UNITS + 8, top = UNITS;
        int[][] box = {
                {-r, 0, -r, -r, 0, r, r, 0, -r}, {r, 0, -r, -r, 0, r, r, 0, r}, // the floor
                {-r, top, z0, -r, top, z1, r, top, z1}, {-r, top, z0, r, top, z1, r, top, z0}, // pane top
                {-r, 0, z0, r, 0, z0, r, 0, z1}, {-r, 0, z0, r, 0, z1, -r, 0, z1}, // pane underside
                {-r, 0, z0, -r, top, z0, r, top, z0}, {-r, 0, z0, r, top, z0, r, 0, z0}, // north side
                {-r, 0, z1, r, 0, z1, r, top, z1}, {-r, 0, z1, r, top, z1, -r, top, z1}, // south side
        };
        // Wind each as CollisionBuilder does: the floor up, the top up, the underside down, the sides outward.
        int[][] normals = {{0, 1, 0}, {0, 1, 0}, {0, 1, 0}, {0, 1, 0}, {0, -1, 0}, {0, -1, 0},
                {0, 0, -1}, {0, 0, -1}, {0, 0, 1}, {0, 0, 1}};
        SM64Surface[] surfaces = (SM64Surface[]) new SM64Surface().toArray(box.length);
        for (int i = 0; i < box.length; i++) {
            int[] t = box[i].clone();
            long ux = t[3] - t[0], uy = t[4] - t[1], uz = t[5] - t[2], vx = t[6] - t[0], vy = t[7] - t[1], vz = t[8] - t[2];
            long cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
            if (cx * normals[i][0] + cy * normals[i][1] + cz * normals[i][2] < 0) {
                int[] b = {t[3], t[4], t[5]};
                System.arraycopy(t, 6, t, 3, 3);
                System.arraycopy(b, 0, t, 6, 3);
            }
            System.arraycopy(t, 0, surfaces[i].vertices, 0, 9);
            if (i >= 2) surfaces[i].type = Surfaces.VANISH_CAP_WALLS;
        }
        lib.sm64_static_surfaces_load(surfaces, surfaces.length);
        SM64MarioInputs inputs = new SM64MarioInputs();
        inputs.camLookZ = -1; // facing +z
        inputs.stickY = 1;
        SM64MarioState state = new SM64MarioState();
        SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();
        float[] reached = new float[2];
        for (int vanish = 0; vanish < 2; vanish++) {
            int id = lib.sm64_mChar_create(0, 0, 0);
            for (int i = 0; i < 60; i++) {
                lib.sm64_mChar_set_caps(id, vanish == 1 ? 0x02 : 0, 0);
                lib.sm64_mChar_tick(id, inputs, state, buffers);
            }
            reached[vanish] = state.position[2];
            lib.sm64_mChar_delete(id);
        }
        assertTrue(reached[0] < z0, "walked through the pane without the cap: z " + reached[0]);
        assertTrue(reached[1] > z1 + UNITS, "the Vanish cap didn't pass the pane: z " + reached[1]);
    }

    /**
     * Metal Mario walking off a one-block ledge under water drops smoothly. SM64's water step checks walls with a 110
     * unit radius, a whole block here, so the ledge's side used to throw him a block forward the moment he fell.
     */
    @Test
    void metalFallsOffAnUnderwaterLedgeSmoothly() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        LibSM64.start(rom, modDir).get(60, TimeUnit.SECONDS);
        Libsm64 lib = LibSM64.lib();
        int r = 3 * UNITS, far = 8 * UNITS, low = -UNITS;
        int[][] tris = {
                {-r, 0, -far, -r, 0, 0, r, 0, -far}, {r, 0, -far, -r, 0, 0, r, 0, 0}, // upper floor, z < 0
                {-r, low, 0, -r, low, far, r, low, 0}, {r, low, 0, -r, low, far, r, low, far}, // lower floor
                {-r, low, 0, r, low, 0, r, 0, 0}, {-r, low, 0, r, 0, 0, -r, 0, 0}, // the ledge's side, facing +z
        };
        SM64Surface[] surfaces = (SM64Surface[]) new SM64Surface().toArray(tris.length);
        for (int i = 0; i < tris.length; i++) System.arraycopy(tris[i], 0, surfaces[i].vertices, 0, 9);
        lib.sm64_static_surfaces_load(surfaces, surfaces.length);
        SM64MarioInputs inputs = new SM64MarioInputs();
        inputs.camLookZ = -1; // facing +z
        SM64MarioState state = new SM64MarioState();
        SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();

        int id = lib.sm64_mChar_create(0, 0, -3 * UNITS);
        lib.sm64_mChar_set_water_level(id, 6 * UNITS);
        float lastZ = Float.NaN, biggestStep = 0;
        inputs.stickY = 1;
        for (int i = 0; i < 150; i++) {
            lib.sm64_mChar_set_caps(id, 0x04, 0);
            lib.sm64_mChar_tick(id, inputs, state, buffers);
            if (i > 0) biggestStep = Math.max(biggestStep, Math.abs(state.position[2] - lastZ));
            lastZ = state.position[2];
        }
        assertTrue(biggestStep < 30, "jumped " + biggestStep + " units in one frame");
        assertEquals(low, state.position[1], 1, "should stand on the lower floor");
        lib.sm64_mChar_delete(id);
    }

    private static int metalTriangles(SM64MarioGeometryBuffers buffers) {
        float[] uv = new float[buffers.triangles() * 6];
        buffers.uv.read(0, uv, 0, uv.length);
        int n = 0;
        for (int t = 0; t < buffers.triangles(); t++) {
            if (!(uv[t * 6] == 1f && uv[t * 6 + 1] == 1f) && uv[t * 6] < 1f / 11) n++;
        }
        return n;
    }

    /** The power meter's textures (D45) come out of the ROM: halves with an opaque rim, eight different middles. */
    @Test
    void readsThePowerMeter() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        RomHud.PowerMeter meter = RomHud.powerMeter(rom.data());
        assertTrue(opaque(meter.left()) > 600 && opaque(meter.right()) > 600,
                "halves mostly transparent: " + opaque(meter.left()) + ", " + opaque(meter.right()));
        java.util.Set<String> middles = new java.util.HashSet<>();
        for (RomHud.Image segment : meter.segments()) {
            assertTrue(opaque(segment) > 300, "a middle is mostly transparent");
            middles.add(java.util.Arrays.toString(segment.rgba()));
        }
        assertEquals(8, middles.size(), "the eight middles differ");
    }

    /**
     * The coin and the HUD glyphs (D50, D57) come out of the ROM: four spin frames, round and narrowing towards the
     * edge-on one, and ten different digits, "×", the coin and the star, each with an opaque middle.
     */
    @Test
    void readsTheCoinArt() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        RomHud.CoinArt art = RomHud.coinArt(rom.data());
        RomHud.Image front = art.spin()[0], edge = art.spin()[2];
        assertTrue(alpha(front, 16, 16) != 0 && alpha(front, 0, 0) == 0, "front frame isn't a round coin");
        assertTrue(opaque(front) > 3 * opaque(edge) && opaque(edge) > 20,
                "edge-on frame isn't narrower: " + opaque(front) + " vs " + opaque(edge));
        java.util.Set<String> digits = new java.util.HashSet<>();
        for (RomHud.Image digit : art.digits()) {
            assertTrue(opaque(digit) > 40, "a digit is empty");
            digits.add(java.util.Arrays.toString(digit.rgba()));
        }
        assertEquals(10, digits.size(), "the ten digits differ");
        for (RomHud.Image glyph : new RomHud.Image[] {art.times(), art.coin(), art.star()}) {
            assertTrue(alpha(glyph, 8, 8) != 0, "a glyph's middle is transparent");
        }
        // The coin glyph is gold (red and green high, blue low), the star yellow.
        int coin = art.coin().argb(8, 5);
        assertTrue((coin >> 16 & 0xFF) > 150 && (coin & 0xFF) < 120, "coin glyph isn't gold: " + Integer.toHexString(coin));
    }

    private static int alpha(RomHud.Image image, int x, int y) {
        return image.argb(x, y) >>> 24;
    }

    private static int opaque(RomHud.Image image) {
        int n = 0;
        for (int i = 3; i < image.rgba().length; i += 4) if (image.rgba()[i] != 0) n++;
        return n;
    }

    private static float[] walk(Libsm64 lib, float forward, float right) {
        int id = lib.sm64_mChar_create(0, 0, 0);
        SM64MarioInputs inputs = new SM64MarioInputs();
        SM64MarioState state = new SM64MarioState();
        SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();
        double yaw = 0;
        inputs.camLookX = (float) Math.sin(yaw);
        inputs.camLookZ = (float) -Math.cos(yaw);
        for (int i = 0; i < 5; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        float x0 = state.position[0], z0 = state.position[2];
        inputs.stickX = -right;
        inputs.stickY = forward;
        for (int i = 0; i < 30; i++) lib.sm64_mChar_tick(id, inputs, state, buffers);
        lib.sm64_mChar_delete(id);
        return new float[] {state.position[0] - x0, state.position[2] - z0};
    }

    /** A 20×20-block floor at y = 0, two triangles facing up (upstream's winding). */
    private static SM64Surface[] floor() {
        int r = 10 * UNITS;
        int[][] a = {{-r, 0, -r}, {-r, 0, r}, {r, 0, -r}};
        int[][] b = {{r, 0, -r}, {-r, 0, r}, {r, 0, r}};
        SM64Surface[] surfaces = (SM64Surface[]) new SM64Surface().toArray(2);
        int[][][] tris = {a, b};
        for (int t = 0; t < 2; t++) {
            for (int v = 0; v < 3; v++) System.arraycopy(tris[t][v], 0, surfaces[t].vertices, v * 3, 3);
        }
        return surfaces;
    }
}
