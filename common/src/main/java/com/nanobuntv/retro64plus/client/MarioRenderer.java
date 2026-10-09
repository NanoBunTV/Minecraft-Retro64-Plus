package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nanobuntv.retro64plus.MarioCharacter;
import com.nanobuntv.retro64plus.MarioHooks;
import com.nanobuntv.retro64plus.sm64.Actions;
import com.nanobuntv.retro64plus.sm64.RomModels;
import com.nanobuntv.retro64plus.sm64.Units;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws Mario in place of the player model (through mixin/client/PlayerRendererMixin): libsm64's triangles, as
 * degenerate quads (the fourth vertex repeats the third) in Minecraft's entity render types.
 * <ul>
 *   <li>The local player: their character's frames interpolated around the render time, so he moves smoothly at any
 *       frame rate. Vertices are absolute, so the player's render position is subtracted. In the inventory, Mario is
 *       drawn around his own position, turned to the viewer.</li>
 *   <li>Other players (M1 checkpoint 3): their puppet's last two poses interpolated by the partial tick, as vanilla
 *       interpolates entities. Vertices are around the origin, so they're drawn where this client shows the player.
 *       Without a puppet yet (no ROM loaded), the vanilla model.</li>
 * </ul>
 * The character (D27) decides how: Mario's triangles with his texture, the same recoloured for Luigi (D33), or the
 * player's skin on a blocky body moved by the same skeleton ({@link SkinBody}, D28). First person shows no body, as
 * vanilla.
 *
 * <p>Caps (M2): the Wing cap's wings are in libsm64's triangles. Metal Mario's triangles all use the ROM's metal texture
 * (atlas slot 0; a frame counts as metal when most of its triangles do) with SM64's environment mapping, which libsm64 doesn't compute: here each vertex's UV comes from its
 * normal as the camera sees it, as the N64's texture generation does. The Vanish cap draws see-through.
 */
public final class MarioRenderer {
    /** {@code SM64MarioState.flags}: the vanish cap is on (drawn see-through, as upstream). */
    static final int FLAG_VANISH_CAP = 0x00000002;

    private static final MarioInstance.Frame[] BRACKET = new MarioInstance.Frame[2];
    /** libsm64's atlas U below this is the metal texture (slot 0 of 11). */
    private static final float METAL_U = 1f / 11;
    private static final Matrix3f VIEW_NORMAL = new Matrix3f();
    private static final Vector3f NORMAL = new Vector3f();
    private static final float[] COLOR = new float[3];
    /** Set by InventoryScreenMixin while the inventory draws the player. */
    private static boolean inInventory;

    private MarioRenderer() {}

    public static void setInInventory(boolean value) {
        inInventory = value;
    }

    public static boolean inInventory() {
        return inInventory;
    }

    /** As {@link #renderPlayer(AbstractClientPlayer, float, PoseStack, Draw, int)} for a state the inventory extracted or not. */
    public static boolean renderPlayer(AbstractClientPlayer player, float partialTick, PoseStack pose, Draw buffers,
            int light, boolean inventory) {
        boolean was = inInventory;
        inInventory = inventory;
        try {
            return renderPlayer(player, partialTick, pose, buffers, light);
        } finally {
            inInventory = was;
        }
    }

    /** From PlayerRenderer.render's head: true if Mario was drawn instead (the vanilla model is then skipped). */
    public static boolean renderPlayer(AbstractClientPlayer player, float partialTick, PoseStack pose,
            Draw buffers, int light) {
        if (MarioHooks.isLocalMario(player)) return renderLocal(player, partialTick, pose, buffers, light);
        if (!MarioHooks.isMario(player)) return false;
        MarioPuppet puppet = RemoteMarios.puppet(player);
        if (puppet == null || puppet.current() == null) return false;
        MarioInstance.Frame a = puppet.previous(), b = puppet.current();
        draw(player, puppet.character(), a, b, partialTick, 0, 0, 0, pose, buffers, light);
        return true;
    }

    private static boolean renderLocal(AbstractClientPlayer player, float partialTick, PoseStack pose,
            Draw buffers, int light) {
        LocalMario local = LocalMario.active();
        if (local == null) return false;
        MarioInstance mario = local.mario();
        if (mario.latest() == null) return true; // no frame yet: draw nothing this once
        float t = mario.bracket(inInventory ? Double.MAX_VALUE : local.renderFrame(partialTick), BRACKET);
        MarioInstance.Frame a = BRACKET[0], b = BRACKET[1];
        pose.pushPose();
        if (inInventory) {
            pose.mulPose(Axis.YP.rotationDegrees(-player.yBodyRot));
            pose.mulPose(Axis.YP.rotation(-mario.state.faceAngle));
            draw(player, local.character(), a, b, t, b.x, b.y, b.z, pose, buffers, light);
        } else {
            draw(player, local.character(), a, b, t, Mth.lerp(partialTick, player.xo, player.getX()),
                    Mth.lerp(partialTick, player.yo, player.getY()), Mth.lerp(partialTick, player.zo, player.getZ()),
                    pose, buffers, light);
        }
        pose.popPose();
        return true;
    }

    /** The own-skin character's size against Mario's (D130). */
    private static final float SKIN_SCALE = 1.5f;

    /** Frames {@code a} → {@code b} at {@code t}, less (ox, oy, oz), as {@code character}. */
    private static void draw(AbstractClientPlayer player, MarioCharacter character, MarioInstance.Frame a,
            MarioInstance.Frame b, float t, double ox, double oy, double oz, PoseStack pose, Draw buffers,
            int light) {
        boolean vanish = (b.flags & FLAG_VANISH_CAP) != 0;
        float alpha = vanish ? 0.3f : 1f;
        if (Actions.has(b.action, Actions.FLAG_RIDING_SHELL)) drawShell(a, b, t, ox, oy, oz, alpha, pose, buffers, light);
        if (character == MarioCharacter.SKIN) {
            // The skin body is drawn SKIN_SCALE times Mario's size, grown from his feet (D130).
            double mx = Mth.lerp(t, a.x, b.x), my = Mth.lerp(t, a.y, b.y), mz = Mth.lerp(t, a.z, b.z);
            pose.pushPose();
            pose.translate(mx - ox, my - oy, mz - oz);
            pose.scale(SKIN_SCALE, SKIN_SCALE, SKIN_SCALE);
            boolean drawn = SkinBody.draw(a, b, t, mx, my, mz, player.getSkin(), alpha, pose, buffers, light);
            pose.popPose();
            if (drawn) return;
        }
        drawTriangles(character == MarioCharacter.LUIGI, a, b, t, ox, oy, oz, vanish, alpha, pose, buffers, light);
    }

    /**
     * The Koopa shell he rides (M4 checkpoint 3), from the ROM: at Mario's position, turned his way, as SM64's riding
     * shell copies his position and yaw (his riding animation lifts him onto it).
     */
    private static void drawShell(MarioInstance.Frame a, MarioInstance.Frame b, float t, double ox, double oy,
            double oz, float alpha, PoseStack pose, Draw buffers, int light) {
        RomModels.Model shell = RomModelRenderer.model(RomModels.Kind.KOOPA_SHELL);
        if (shell == null) return;
        pose.pushPose();
        pose.translate(Mth.lerp(t, a.x, b.x) - ox, Mth.lerp(t, a.y, b.y) - oy, Mth.lerp(t, a.z, b.z) - oz);
        boolean bothRiding = Actions.has(a.action, Actions.FLAG_RIDING_SHELL);
        float yaw = b.faceAngle;
        if (bothRiding) yaw = a.faceAngle + t * Mth.wrapDegrees((float) Math.toDegrees(b.faceAngle - a.faceAngle))
                * Mth.DEG_TO_RAD;
        pose.mulPose(Axis.YP.rotation(yaw));
        float s = 1 / Units.PER_BLOCK;
        pose.scale(s, s, s);
        RomModelRenderer.render(shell, null, 0, 0, pose, buffers, light, OverlayTexture.NO_OVERLAY, alpha);
        pose.popPose();
    }

    /** libsm64's triangles, interpolated only when both frames have the same ones. */
    private static void drawTriangles(boolean luigi, MarioInstance.Frame a, MarioInstance.Frame b, float t, double ox,
            double oy, double oz, boolean vanish, float alpha, PoseStack pose, Draw buffers, int light) {
        boolean lerp = a != b && a.triangles == b.triangles;
        var texture = luigi ? MarioTexture.LUIGI_ID : MarioTexture.ID;
        VertexConsumer out = buffers.getBuffer(vanish ? ModRenderTypes.entityTranslucent(texture)
                : ModRenderTypes.entityCutoutNoCull(texture));
        PoseStack.Pose last = pose.last();
        // Model normals to the camera's view: the pose has the model's turns, the camera its own rotation.
        Quaternionf toView = ClientCompat.cameraRotation().conjugate();
        VIEW_NORMAL.set(toView).mul(last.normal());
        for (int tri = 0; tri < b.triangles; tri++) {
            int uvBase = tri * 6;
            boolean textured = !(b.uvs[uvBase] == 1f && b.uvs[uvBase + 1] == 1f);
            for (int corner = 0; corner < 4; corner++) {
                int v = tri * 3 + Math.min(corner, 2);
                int p = v * 3;
                float x = b.positions[p], y = b.positions[p + 1], z = b.positions[p + 2];
                if (lerp) {
                    x = Mth.lerp(t, a.positions[p], x);
                    y = Mth.lerp(t, a.positions[p + 1], y);
                    z = Mth.lerp(t, a.positions[p + 2], z);
                }
                float r = 1, g = 1, bl = 1, u, w;
                if (textured && b.metal) {
                    // Metal: SM64's texture generation, s and t from the view-space normal (the 64 × 32 metal texture).
                    NORMAL.set(b.normals[p], b.normals[p + 1], b.normals[p + 2]);
                    VIEW_NORMAL.transform(NORMAL);
                    float s = 0.5f + 0.5f * NORMAL.x(), tt = 0.5f - 0.5f * NORMAL.y();
                    u = Math.clamp(s, 0.01f, 0.99f) * METAL_U * MarioTexture.U_SCALE;
                    w = Math.clamp(tt, 0.01f, 0.99f) * 0.5f;
                } else if (textured) {
                    u = b.uvs[v * 2] * MarioTexture.U_SCALE + 0.5f / MarioTexture.WIDTH;
                    w = b.uvs[v * 2 + 1] + 0.5f / MarioTexture.HEIGHT;
                } else if (luigi) {
                    MarioTexture.luigiColor(b.colors[p], b.colors[p + 1], b.colors[p + 2], COLOR);
                    r = COLOR[0];
                    g = COLOR[1];
                    bl = COLOR[2];
                    u = MarioTexture.WHITE_U;
                    w = MarioTexture.WHITE_V;
                } else {
                    r = b.colors[p];
                    g = b.colors[p + 1];
                    bl = b.colors[p + 2];
                    u = MarioTexture.WHITE_U;
                    w = MarioTexture.WHITE_V;
                }
                out.addVertex(last, (float) (x - ox), (float) (y - oy), (float) (z - oz))
                        .setColor(r, g, bl, alpha)
                        .setUv(u, w)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(last, b.normals[p], b.normals[p + 1], b.normals[p + 2]);
            }
        }
    }
}
