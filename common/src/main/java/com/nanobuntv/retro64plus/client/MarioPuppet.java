package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.MarioCharacter;
import com.nanobuntv.retro64plus.net.MarioPose;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import com.nanobuntv.retro64plus.sm64.lib.SM64MarioGeometryBuffers;
import com.nanobuntv.retro64plus.sm64.lib.SM64MarioPose;
import org.jetbrains.annotations.Nullable;

/**
 * Another player's Mario on this client (M1 checkpoint 3): a libsm64 puppet that never runs physics, drawn in the pose
 * its owner sends every tick (sm64_mChar_render_pose). Its geometry is around the origin, so it's drawn at wherever
 * this client shows that player. Keeps the last two poses' triangles to interpolate between, like vanilla entities.
 * Render thread only (libsm64 is single-threaded).
 */
final class MarioPuppet {
    /** {@code MODEL_STATE_ALPHA} in the decomp's mario_misc. */
    private static final int MODEL_STATE_ALPHA = 0x100;

    private final int id;
    private final SM64MarioGeometryBuffers buffers = new SM64MarioGeometryBuffers();
    private final SM64MarioPose pose = new SM64MarioPose();
    private MarioInstance.Frame previous = new MarioInstance.Frame(), current = new MarioInstance.Frame();
    private int shown;
    private MarioCharacter character = MarioCharacter.MARIO;

    private MarioPuppet(int id) {
        this.id = id;
    }

    static @Nullable MarioPuppet create() {
        int id = LibSM64.lib().sm64_mChar_create_puppet();
        return id < 0 ? null : new MarioPuppet(id);
    }

    /** Draws the next pose; the previous one is kept for interpolation. */
    void show(MarioPose next) {
        next.into(pose);
        // libsm64 draws Mario's model for every character: Luigi is recoloured and the skin body uses its bones.
        LibSM64.lib().sm64_mChar_render_pose(id, pose, 0, buffers);
        character = MarioCharacter.byId(next.model());
        MarioInstance.Frame swap = previous;
        previous = current;
        current = swap;
        current.read(buffers);
        // The renderer draws see-through on the vanish cap flag; a puppet has no state flags, so take it from SM64's
        // model state (MODEL_STATE_ALPHA, set while the vanish cap is on).
        current.flags = (next.modelState() & MODEL_STATE_ALPHA) != 0 ? MarioRenderer.FLAG_VANISH_CAP : 0;
        current.action = next.action();
        current.faceAngle = (float) (next.angleY() * Math.PI / 32768);
        shown++;
    }

    /** The character the owner plays (D27), from their last pose. */
    MarioCharacter character() {
        return character;
    }

    /** The newest frame, or null before any pose. */
    @Nullable MarioInstance.Frame current() {
        return shown == 0 ? null : current;
    }

    /** The frame before it (the newest again until there have been two). */
    MarioInstance.Frame previous() {
        return shown < 2 ? current : previous;
    }

    void delete() {
        LibSM64.lib().sm64_mChar_delete(id);
    }
}
