package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nanobuntv.retro64plus.sm64.Units;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The own-skin character (D28): a blocky Minecraft body in the player's skin, both layers, wide or slim arms as the skin
 * says, moved by SM64's skeleton. Mario's model is still run (for his bones and his movement) but not drawn.
 *
 * <p>Each part is a box on one bone, in the skin's usual layout; arms and legs are two boxes each (upper and lower half,
 * the skin's limb texture split in two) on the upper and lower bone. Lengths follow Mario's bones, so the joints meet;
 * widths are {@link #PIXEL} SM64 units per skin pixel. A limb box takes the direction of its bone, with no twist: its
 * front is the body's front, swung the shortest way along with the limb, so the skin's faces stay where a player
 * expects them whatever the animation does.
 *
 * <p>Bone numbers are libsm64's drawing order for Mario (LibSM64RealRomTest prints them): 1 pelvis, 2 torso, 3 head,
 * then shoulder, upper arm, forearm, hand for the left (4–7) and right arm (8–11), and hip, thigh, shin, foot for the
 * left (12–15) and right leg (16–19). A bone's x axis runs along it; for the pelvis, torso and head x is up, y forward and
 * z to Mario's left.
 */
final class SkinBody {
    private static final int PELVIS = 1, TORSO = 2, HEAD = 3;
    private static final int L_SHOULDER = 4, L_UPPER_ARM = 5, L_FOREARM = 6, R_SHOULDER = 8, R_UPPER_ARM = 9,
            R_FOREARM = 10, L_HIP = 12, L_THIGH = 13, L_SHIN = 14, R_HIP = 16, R_THIGH = 17, R_SHIN = 18;
    static final int BONES_NEEDED = 20;

    /** Blocks per skin pixel: 3 SM64 units. */
    private static final float PIXEL = 3f / Units.PER_BLOCK;
    private static final float U = 1f / Units.PER_BLOCK;
    /** Along the torso bone: the hips and the neck at rest (SM64 units from the bone's origin). */
    private static final float HIP = -13.6f * U, NECK = 21.1f * U;
    /** Upper arm from just above the shoulder joint; forearm (with the hand); thigh; shin (with the foot). */
    private static final float ARM_TOP = -4f * U, UPPER_ARM = 16.3f * U, FOREARM = 23f * U, THIGH = 22f * U,
            SHIN = 22f * U;
    /** Mario's hips and shoulders are wider apart than a player's: move the limbs this far in (SM64 units). */
    private static final float LEG_IN = 4.5f * U, WIDE_ARM_IN = 1.7f * U, SLIM_ARM_IN = 3.2f * U;
    private static final float OUTER_HEAD = 0.5f, OUTER = 0.25f;

    private SkinBody() {}

    /** One interpolated bone: origin and axes (x along the bone). */
    private static final class Bone {
        final Vector3f origin = new Vector3f(), x = new Vector3f(), y = new Vector3f(), z = new Vector3f();
    }

    /** A box's frame: where it hangs from and its down, front and left directions. */
    private static final class Frame {
        final Vector3f origin = new Vector3f(), down = new Vector3f(), front = new Vector3f(), left = new Vector3f();
    }

    private static final Bone[] BONES = new Bone[BONES_NEEDED];
    private static final Frame FRAME = new Frame();
    static {
        for (int i = 0; i < BONES_NEEDED; i++) BONES[i] = new Bone();
    }

    /** Draws the body for frames {@code a} → {@code b} at {@code t}, less (ox, oy, oz). False if there aren't bones. */
    static boolean draw(MarioInstance.Frame a, MarioInstance.Frame b, float t, double ox, double oy, double oz,
            PlayerSkin skin, float alpha, PoseStack pose, Draw buffers, int light) {
        if (b.bones < BONES_NEEDED) return false;
        MarioInstance.Frame from = a.bones >= BONES_NEEDED ? a : b;
        for (int i = 0; i < BONES_NEEDED; i++) lerpBone(from, b, t, i, ox, oy, oz, BONES[i]);
        boolean slim = skin.model() == PlayerSkin.Model.SLIM;
        int armW = slim ? 3 : 4;
        Out out = new Out(buffers.getBuffer(ModRenderTypes.entityTranslucent(skin.texture())), pose.last(), light, alpha);

        // Head and body: their bones' own axes.
        boneFrame(BONES[HEAD], FRAME);
        out.box(FRAME, -8 * PIXEL, 0, 4 * PIXEL, 4 * PIXEL, 0, 0, 8, 8, 8, 0, 8, true, true, 0);
        out.box(FRAME, -8 * PIXEL, 0, 4 * PIXEL, 4 * PIXEL, 32, 0, 8, 8, 8, 0, 8, true, true, OUTER_HEAD);
        boneFrame(BONES[TORSO], FRAME);
        out.box(FRAME, -NECK, -HIP, 2 * PIXEL, 4 * PIXEL, 16, 16, 8, 12, 4, 0, 12, true, true, 0);
        out.box(FRAME, -NECK, -HIP, 2 * PIXEL, 4 * PIXEL, 16, 32, 8, 12, 4, 0, 12, true, true, OUTER);

        Bone torso = BONES[TORSO], pelvis = BONES[PELVIS];
        float armIn = slim ? SLIM_ARM_IN : WIDE_ARM_IN;
        // Right arm (the skin's 40,16 / 40,32) and left arm (32,48 / 48,48).
        limb(out, R_UPPER_ARM, R_FOREARM, torso, BONES[R_SHOULDER], armIn, ARM_TOP, UPPER_ARM, FOREARM, armW,
                40, 16, 40, 32);
        limb(out, L_UPPER_ARM, L_FOREARM, torso, BONES[L_SHOULDER], -armIn, ARM_TOP, UPPER_ARM, FOREARM, armW,
                32, 48, 48, 48);
        // Right leg (0,16 / 0,32) and left leg (16,48 / 0,48).
        limb(out, R_THIGH, R_SHIN, pelvis, BONES[R_HIP], LEG_IN, 0, THIGH, SHIN, 4, 0, 16, 0, 32);
        limb(out, L_THIGH, L_SHIN, pelvis, BONES[L_HIP], -LEG_IN, 0, THIGH, SHIN, 4, 16, 48, 0, 48);
        return true;
    }

    /**
     * An arm or leg: the upper half on {@code upper}'s bone, the lower on {@code lower}'s, both moved {@code in} along
     * the joint's sideways axis (towards the body for the right side when positive), facing the {@code body}'s front.
     */
    private static void limb(Out out, int upper, int lower, Bone body, Bone joint, float in, float top, float upperLen,
            float lowerLen, int width, int u, int v, int outerU, int outerV) {
        float halfW = width / 2f * PIXEL, halfD = 2 * PIXEL;
        Vector3f shift = new Vector3f(joint.z).mul(in);
        limbFrame(BONES[upper], body, shift, FRAME);
        out.box(FRAME, top, upperLen, halfD, halfW, u, v, width, 12, 4, 0, 6, true, false, 0);
        out.box(FRAME, top, upperLen, halfD, halfW, outerU, outerV, width, 12, 4, 0, 6, true, false, OUTER);
        limbFrame(BONES[lower], body, shift, FRAME);
        out.box(FRAME, 0, lowerLen, halfD, halfW, u, v, width, 12, 4, 6, 12, false, true, 0);
        out.box(FRAME, 0, lowerLen, halfD, halfW, outerU, outerV, width, 12, 4, 6, 12, false, true, OUTER);
    }

    /** Head and body: down is the bone's -x, front its y, left its z. */
    private static void boneFrame(Bone bone, Frame frame) {
        frame.origin.set(bone.origin);
        frame.down.set(bone.x).negate();
        frame.front.set(bone.y);
        frame.left.set(bone.z);
    }

    /**
     * A limb box: down along the bone; front the body's front turned by the shortest rotation that takes the body's
     * down to the bone's direction (no twist); left = front × down.
     */
    private static void limbFrame(Bone bone, Bone body, Vector3f shift, Frame frame) {
        frame.origin.set(bone.origin).add(shift);
        frame.down.set(bone.x);
        Vector3f restDown = new Vector3f(body.x).negate();
        Vector3f axis = new Vector3f(restDown).cross(frame.down);
        float sin = axis.length(), cos = restDown.dot(frame.down);
        if (sin < 1e-5f) {
            frame.front.set(body.y);
            if (cos < 0) frame.front.negate(); // straight up: turned over the body's left axis
        } else {
            axis.div(sin);
            Vector3f v = body.y;
            // Rodrigues: v cos + (axis × v) sin + axis (axis · v)(1 - cos)
            Vector3f cross = new Vector3f(axis).cross(v).mul(sin);
            float along = axis.dot(v) * (1 - cos);
            frame.front.set(v).mul(cos).add(cross).add(axis.x * along, axis.y * along, axis.z * along).normalize();
        }
        frame.left.set(frame.front).cross(frame.down).normalize();
    }

    private static void lerpBone(MarioInstance.Frame a, MarioInstance.Frame b, float t, int index, double ox,
            double oy, double oz, Bone out) {
        int o = index * MarioInstance.Frame.BONE_FLOATS;
        float[] da = a.boneData, db = b.boneData;
        out.origin.set((float) (Mth.lerp(t, da[o], db[o]) - ox), (float) (Mth.lerp(t, da[o + 1], db[o + 1]) - oy),
                (float) (Mth.lerp(t, da[o + 2], db[o + 2]) - oz));
        lerpAxis(da, db, o + 3, t, out.x);
        lerpAxis(da, db, o + 6, t, out.y);
        lerpAxis(da, db, o + 9, t, out.z);
    }

    private static void lerpAxis(float[] a, float[] b, int o, float t, Vector3f out) {
        out.set(Mth.lerp(t, a[o], b[o]), Mth.lerp(t, a[o + 1], b[o + 1]), Mth.lerp(t, a[o + 2], b[o + 2]));
        if (out.lengthSquared() < 1e-8f) out.set(b[o], b[o + 1], b[o + 2]);
        out.normalize();
    }

    /** Writes boxes as quads. */
    private record Out(VertexConsumer consumer, PoseStack.Pose pose, int light, float alpha) {
        /**
         * A box in {@code f}: from {@code d0} to {@code d1} along down, ±{@code halfFront} and ±{@code halfLeft},
         * grown by {@code inflate} skin pixels. Textured from the skin box at (u, v) of w × h × d pixels, using rows
         * {@code h0}..{@code h1} of its sides, with its top and/or bottom face.
         */
        void box(Frame f, float d0, float d1, float halfFront, float halfLeft, int u, int v, int w, int h, int d,
                int h0, int h1, boolean top, boolean bottom, float inflate) {
            float g = inflate * PIXEL;
            d0 -= g;
            d1 += g;
            float fr = halfFront + g, lf = halfLeft + g;
            float sideTop = v + d + h0, sideBottom = v + d + h1;
            // front (+front): u right (-left) → left; back (-front): u left → right
            quad(f, d0, d1, fr, -lf, fr, lf, u + d, sideTop, u + d + w, sideBottom, f.front, 1);
            quad(f, d0, d1, -fr, lf, -fr, -lf, u + 2 * d + w, sideTop, u + 2 * d + 2 * w, sideBottom, f.front, -1);
            // right side (-left): u back → front; left side (+left): u front → back
            quad(f, d0, d1, -fr, -lf, fr, -lf, u, sideTop, u + d, sideBottom, f.left, -1);
            quad(f, d0, d1, fr, lf, -fr, lf, u + d + w, sideTop, u + 2 * d + w, sideBottom, f.left, 1);
            if (top) cap(f, d0, fr, lf, u + d, v, u + d + w, v + d, -1);
            if (bottom) cap(f, d1, fr, lf, u + d + w, v, u + d + 2 * w, v + d, 1);
        }

        /** A side face from corner (f1, l1) to (f2, l2), d0 to d1 along down; texture left edge at the first corner. */
        private void quad(Frame f, float d0, float d1, float f1, float l1, float f2, float l2, float u0, float v0,
                float u1, float v1, Vector3f normalAxis, float normalSign) {
            float nx = normalAxis.x * normalSign, ny = normalAxis.y * normalSign, nz = normalAxis.z * normalSign;
            vertex(f, d0, f1, l1, u0, v0, nx, ny, nz);
            vertex(f, d1, f1, l1, u0, v1, nx, ny, nz);
            vertex(f, d1, f2, l2, u1, v1, nx, ny, nz);
            vertex(f, d0, f2, l2, u1, v0, nx, ny, nz);
        }

        /** The top (sign -1, at d0) or bottom (sign 1) face: u from right to left, v from back to front. */
        private void cap(Frame f, float d, float fr, float lf, float u0, float v0, float u1, float v1, float sign) {
            float nx = f.down.x * sign, ny = f.down.y * sign, nz = f.down.z * sign;
            vertex(f, d, -fr, -lf, u0, v0, nx, ny, nz);
            vertex(f, d, fr, -lf, u0, v1, nx, ny, nz);
            vertex(f, d, fr, lf, u1, v1, nx, ny, nz);
            vertex(f, d, -fr, lf, u1, v0, nx, ny, nz);
        }

        private void vertex(Frame f, float down, float front, float left, float u, float v, float nx, float ny,
                float nz) {
            float x = f.origin.x + f.down.x * down + f.front.x * front + f.left.x * left;
            float y = f.origin.y + f.down.y * down + f.front.y * front + f.left.y * left;
            float z = f.origin.z + f.down.z * down + f.front.z * front + f.left.z * left;
            consumer.addVertex(pose, x, y, z).setColor(1f, 1f, 1f, alpha).setUv(u / 64f, v / 64f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        }
    }
}
