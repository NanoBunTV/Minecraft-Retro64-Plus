package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.net.MarioPose;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import com.nanobuntv.retro64plus.sm64.Units;
import com.nanobuntv.retro64plus.sm64.lib.Libsm64;
import com.nanobuntv.retro64plus.sm64.lib.SM64MarioGeometryBuffers;
import com.nanobuntv.retro64plus.sm64.lib.SM64MarioInputs;
import com.nanobuntv.retro64plus.sm64.lib.SM64MarioPose;
import com.nanobuntv.retro64plus.sm64.lib.SM64MarioState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One libsm64 character on this client. {@link #frame()} runs one SM64 frame (1/30 s); the last {@link #HISTORY}
 * frames are kept, positions in blocks, so rendering can interpolate between frames at any frame rate. Render
 * thread only.
 */
final class MarioInstance {
    static final int HISTORY = 4;
    private static final int MAX_VERTICES = SM64MarioGeometryBuffers.VERTICES;

    final int id;
    final SM64MarioInputs inputs = new SM64MarioInputs();
    final SM64MarioState state = new SM64MarioState();
    private final SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();
    private final Frame[] frames = new Frame[HISTORY];
    /** Frames run since creation; frame n is at time n / 30 s. */
    private long frameCount;

    /**
     * One frame's output. Positions in blocks (absolute); normals, colours and UVs as libsm64 gives them. Also the
     * skeleton (D28): per bone {@link #BONE_FLOATS} floats, its origin in blocks then its x, y and z axes as unit
     * vectors (x runs along the bone, to its child).
     */
    static final class Frame {
        static final int BONE_FLOATS = 12;
        private static final float[] RAW_BONES = new float[Libsm64.MAX_BONES * 16];

        long index = -1;
        double x, y, z;
        int triangles;
        final float[] positions = new float[MAX_VERTICES * 3];
        final float[] normals = new float[MAX_VERTICES * 3];
        final float[] colors = new float[MAX_VERTICES * 3];
        final float[] uvs = new float[MAX_VERTICES * 2];
        int bones;
        final float[] boneData = new float[Libsm64.MAX_BONES * BONE_FLOATS];
        int flags;
        int action;
        /** Facing, SM64's yaw in radians (the Koopa shell under him follows it). */
        float faceAngle;
        /** Drawn as Metal Mario: most triangles use the metal texture (atlas slot 0), see {@link MarioRenderer}. */
        boolean metal;

        /** Copies what libsm64 just drew: triangles (positions converted to blocks) and bones. */
        void read(SM64MarioGeometryBuffers buffers) {
            triangles = buffers.triangles();
            int vertices = triangles * 3;
            buffers.position.read(0, positions, 0, vertices * 3);
            buffers.normal.read(0, normals, 0, vertices * 3);
            buffers.color.read(0, colors, 0, vertices * 3);
            buffers.uv.read(0, uvs, 0, vertices * 2);
            for (int i = 0; i < vertices * 3; i++) positions[i] /= Units.PER_BLOCK;
            int metalTriangles = 0;
            for (int t = 0; t < triangles; t++) {
                float u = uvs[t * 6], v = uvs[t * 6 + 1];
                if (!(u == 1f && v == 1f) && u < 1f / 11) metalTriangles++;
            }
            metal = metalTriangles > triangles / 2;
            bones = LibSM64.lib().sm64_last_render_bones(RAW_BONES, Libsm64.MAX_BONES);
            for (int b = 0; b < bones; b++) {
                int in = b * 16, out = b * BONE_FLOATS;
                for (int c = 0; c < 3; c++) boneData[out + c] = RAW_BONES[in + 12 + c] / Units.PER_BLOCK;
                for (int axis = 0; axis < 3; axis++) {
                    int a = in + axis * 4, o = out + 3 + axis * 3;
                    float ax = RAW_BONES[a], ay = RAW_BONES[a + 1], az = RAW_BONES[a + 2];
                    float length = (float) Math.sqrt(ax * ax + ay * ay + az * az);
                    if (length < 1e-6f) length = 1;
                    boneData[o] = ax / length;
                    boneData[o + 1] = ay / length;
                    boneData[o + 2] = az / length;
                }
            }
        }
    }

    private MarioInstance(int id) {
        this.id = id;
        for (int i = 0; i < HISTORY; i++) frames[i] = new Frame();
    }

    /** A new character at {@code pos} (blocks); null if libsm64 refused (no floor below). */
    static MarioInstance create(Vec3 pos) {
        int id = LibSM64.lib().sm64_mChar_create(Units.toSm64(pos.x), Units.toSm64(pos.y), Units.toSm64(pos.z));
        if (id < 0) return null;
        MarioInstance mario = new MarioInstance(id);
        mario.setStatePosition(pos);
        return mario;
    }

    /**
     * libsm64 only fills {@link #state} during a tick; until the next one, {@link #position()} must already say where
     * Mario was put, or the collision gets built around the origin and he has no floor (a crash in libsm64).
     */
    private void setStatePosition(Vec3 pos) {
        state.position[0] = Units.toSm64(pos.x);
        state.position[1] = Units.toSm64(pos.y);
        state.position[2] = Units.toSm64(pos.z);
    }

    /** Runs one SM64 frame with the current {@link #inputs}. */
    void frame() {
        LibSM64.lib().sm64_mChar_tick(id, inputs, state, buffers);
        Frame f = frames[(int) (frameCount % HISTORY)];
        f.index = frameCount++;
        f.x = Units.toBlocks(state.position[0]);
        f.y = Units.toBlocks(state.position[1]);
        f.z = Units.toBlocks(state.position[2]);
        f.flags = state.flags;
        f.action = state.action;
        f.faceAngle = state.faceAngle;
        f.read(buffers);
    }

    /** This character's pose, for the network (sm64_mChar_get_pose), with the character as its model byte. */
    MarioPose pose(int character) {
        LibSM64.lib().sm64_mChar_get_pose(id, poseStruct);
        return MarioPose.of(poseStruct, character);
    }

    /** The sounds Mario's code played during the last frame (D32), into {@code out}; returns how many. */
    int lastFrameSounds(int[] out) {
        return LibSM64.lib().sm64_mChar_last_tick_sounds(out, out.length);
    }

    private final SM64MarioPose poseStruct = new SM64MarioPose();

    long frameCount() {
        return frameCount;
    }

    /** The newest frame, or null before the first. */
    Frame latest() {
        return frameCount == 0 ? null : frames[(int) ((frameCount - 1) % HISTORY)];
    }

    /**
     * The two kept frames around {@code time} (in frames since creation), into {@code out[0..1]}; returns how far
     * between them (0..1). Clamps to the oldest or newest frame.
     */
    float bracket(double time, Frame[] out) {
        Frame newest = latest();
        long oldest = Math.max(0, frameCount - HISTORY);
        if (time >= newest.index) {
            out[0] = out[1] = newest;
            return 0;
        }
        if (time <= oldest) {
            out[0] = out[1] = frames[(int) (oldest % HISTORY)];
            return 0;
        }
        long before = (long) Math.floor(time);
        out[0] = frames[(int) (before % HISTORY)];
        out[1] = frames[(int) ((before + 1) % HISTORY)];
        return (float) (time - before);
    }

    Vec3 position() {
        return new Vec3(Units.toBlocks(state.position[0]), Units.toBlocks(state.position[1]),
                Units.toBlocks(state.position[2]));
    }

    /** Moves Mario and restarts the frame history, so rendering doesn't sweep across the jump. */
    void teleport(Vec3 pos) {
        teleportKeepingHistory(pos);
        frameCount = 0;
    }

    /** Moves Mario without touching the history (small, per-frame moves: riding, getting into bed). */
    void teleportKeepingHistory(Vec3 pos) {
        LibSM64.lib().sm64_mChar_teleport(id, Units.toSm64(pos.x), Units.toSm64(pos.y), Units.toSm64(pos.z));
        setStatePosition(pos);
    }

    /** Velocity in blocks per SM64 frame. */
    Vec3 velocity() {
        return new Vec3(Units.toBlocks(state.velocity[0]), Units.toBlocks(state.velocity[1]),
                Units.toBlocks(state.velocity[2]));
    }

    void setVelocity(Vec3 blocksPerFrame) {
        LibSM64.lib().sm64_mChar_set_velocity(id, Units.toSm64(blocksPerFrame.x), Units.toSm64(blocksPerFrame.y),
                Units.toSm64(blocksPerFrame.z));
    }

    /** Sets SM64's health (D36: Minecraft owns it); returns the damage SM64's own code dealt since the last call. */
    int setHealth(short health) {
        return LibSM64.lib().sm64_mChar_set_health(id, health);
    }

    /** The caps Mario wears and those flickering off this frame ({@code MARIO_*_CAP} flags, D17). */
    void setCaps(int caps, int hidden) {
        LibSM64.lib().sm64_mChar_set_caps(id, caps, hidden);
    }

    /** Minecraft hurt Mario: SM64's knockback away from {@code source} (blocks; none: just his voice). */
    void knockback(int segments, @Nullable Vec3 source) {
        if (source == null) {
            LibSM64.lib().sm64_mChar_knockback(id, segments, 0, 0, 0, 0);
        } else {
            LibSM64.lib().sm64_mChar_knockback(id, segments, 1, Units.toSm64(source.x), Units.toSm64(source.y),
                    Units.toSm64(source.z));
        }
    }

    /** M4: SM64's pick-up of an object (a Bob-omb); false if he can't now (in the air, swimming, holding). */
    boolean grab() {
        return LibSM64.lib().sm64_mChar_grab(id) != 0;
    }

    /** M4: whether he still holds (or is picking up) the object. */
    boolean holding() {
        return LibSM64.lib().sm64_mChar_is_holding(id) != 0;
    }

    /** M4: lets go of the object without throwing it. */
    void drop() {
        LibSM64.lib().sm64_mChar_drop(id);
    }

    /** M4 checkpoint 3: gets on a Koopa shell; false if he can't. */
    boolean rideShell() {
        return LibSM64.lib().sm64_mChar_ride_shell(id) != 0;
    }

    /** M4 checkpoint 3: whether he still rides the shell. */
    boolean riding() {
        return LibSM64.lib().sm64_mChar_is_riding(id) != 0;
    }

    void setAction(int action) {
        LibSM64.lib().sm64_mChar_set_action(id, action);
    }

    void setAction(int action, int arg) {
        LibSM64.lib().sm64_mChar_set_action_with_arg(id, action, arg);
    }

    void setActionState(int actionState) {
        LibSM64.lib().sm64_mChar_set_action_state(id, (short) actionState);
    }

    /** Yaw in radians, SM64's convention (0 faces +z, growing towards +x). */
    void setAngle(double radians) {
        LibSM64.lib().sm64_mChar_set_angle(id, Units.nativeAngle(radians));
    }

    /** Water surface height in blocks; {@link Double#NEGATIVE_INFINITY} for no water. */
    void setWaterLevel(double blocks) {
        LibSM64.lib().sm64_mChar_set_water_level(id, nativeLevel(blocks));
    }

    /**
     * Poison gas surface height in blocks (Mario coughs and loses health below it); {@link Double#NEGATIVE_INFINITY}
     * for none, {@link Double#POSITIVE_INFINITY} for gas everywhere.
     */
    void setGasLevel(double blocks) {
        LibSM64.lib().sm64_mChar_set_gas_level(id, nativeLevel(blocks));
    }

    /** A height for libsm64's 32-bit level fields; the infinities become far out of any world's range. */
    private static int nativeLevel(double blocks) {
        if (blocks == Double.NEGATIVE_INFINITY) return -10_000_000;
        if (blocks == Double.POSITIVE_INFINITY) return 10_000_000;
        return Math.round(Units.toSm64(blocks));
    }

    void delete() {
        LibSM64.lib().sm64_mChar_delete(id);
    }
}
