package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import com.nanobuntv.retro64plus.sm64.RomModels;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws SM64 object models from the ROM (M4, D19) in Minecraft: poses them with {@link RomModels#pose}, then sends each
 * batch's triangles as degenerate quads (entity render types draw quads) with SM64's shading: lights in view space,
 * texture generation from the view-space normal, and the color combiner's texture/shade mix. Render thread. SM64's mirrored
 * texture wrap is baked into a texture twice as wide or tall (a mirror image beside it), whose plain repeat is the same
 * (Minecraft 1.21.5+ samplers repeat or clamp only).
 */
public final class RomModelRenderer {
    /**
     * One model's textures in Minecraft, each as drawn (with its alpha) and as an opaque copy, and the factor its u and v
     * scale by (1/2 on a mirrored axis, whose texture is twice the size).
     */
    private record Textures(ResourceLocation[] normal, ResourceLocation[] opaque, float[] uScale, float[] vScale) {}

    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID,
            "dynamic/rom_model/white");
    private static final Map<RomModels.Kind, Textures> TEXTURES = new EnumMap<>(RomModels.Kind.class);
    private static boolean whiteRegistered, triedRom;

    private static final Matrix3f MODEL_TO_VIEW = new Matrix3f();
    private static final Vector3f VEC = new Vector3f();

    private RomModelRenderer() {}

    /**
     * The model, if the ROM's is available (the caller draws its own look otherwise). The first ask starts loading the
     * library quietly, as for other players' Marios, so objects use the ROM even before you turn into Mario.
     */
    public static RomModels.@Nullable Model model(RomModels.Kind kind) {
        if (!triedRom && LibSM64.status() == LibSM64.Status.NOT_STARTED) {
            triedRom = true;
            Retro64PlusClient.loadQuietly();
        }
        return LibSM64.model(kind);
    }

    /**
     * Draws {@code model} at the pose stack's origin, in SM64 units (the caller scales units to blocks and turns it to
     * face its yaw; SM64 models face +z). {@code frame} counts 30 Hz animation steps since the animation started.
     */
    public static void render(RomModels.Model model, RomModels.@Nullable Animation animation, float frame,
            int animState, PoseStack poseStack, Draw buffers, int light, int overlay, float alpha) {
        // In the world the pose has the model's turns and the camera its own rotation.
        Quaternionf camera = ClientCompat.cameraRotation();
        Matrix3f modelToView = new Matrix3f().set(camera.conjugate()).mul(poseStack.last().normal());
        render(model, animation, frame, animState, poseStack, buffers, light, overlay, alpha, modelToView);
    }

    /**
     * As {@link #render(RomModels.Model, RomModels.Animation, float, int, PoseStack, Draw, int, int, float)}
     * with the turn from model space to view space given (x right, y up, z towards the viewer): for SM64's lights and
     * texture generation, and to turn billboards to the viewer. Screens use this.
     */
    public static void render(RomModels.Model model, RomModels.@Nullable Animation animation, float frame,
            int animState, PoseStack poseStack, Draw buffers, int light, int overlay, float alpha,
            Matrix3f modelToView) {
        Textures textures = textures(model);
        PoseStack.Pose pose = poseStack.last();
        MODEL_TO_VIEW.set(modelToView);
        // The viewer's axes in model space, rows right, up and back (for billboards).
        Matrix3f viewToModel = new Matrix3f(modelToView).invert();
        float[] billboard = new float[9];
        for (int i = 0; i < 3; i++) {
            Vector3f axis = viewToModel.transform(new Vector3f(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0));
            axis.normalize();
            billboard[i * 3] = axis.x;
            billboard[i * 3 + 1] = axis.y;
            billboard[i * 3 + 2] = axis.z;
        }
        RomModels.pose(model, animation, frame, animState, billboard, (mesh, layer, m) -> {
            for (RomModels.Batch batch : mesh.batches()) {
                draw(batch, layer, m, textures, pose, buffers, light, overlay, alpha);
            }
        });
    }

    private static void draw(RomModels.Batch batch, int layer, float[] m, Textures textures, PoseStack.Pose pose,
            Draw buffers, int light, int overlay, float alpha) {
        boolean textured = batch.texture >= 0 && batch.colorMode != RomModels.COLOR_SHADE;
        ResourceLocation texture = !textured ? WHITE
                : batch.alphaMode == RomModels.ALPHA_TEXTURE ? textures.normal[batch.texture]
                : textures.opaque[batch.texture];
        int constant = batch.alphaMode == RomModels.ALPHA_CONSTANT ? batch.constantAlpha : 255;
        boolean translucent = layer == RomModels.LAYER_TRANSPARENT || alpha < 1 || constant < 255;
        VertexConsumer out = buffers.getBuffer(translucent ? ModRenderTypes.entityTranslucent(texture)
                : batch.cullBack ? ModRenderTypes.entityCutoutCull(texture) : ModRenderTypes.entityCutoutNoCull(texture));
        float uScale = textured ? textures.uScale[batch.texture] : 1, vScale = textured ? textures.vScale[batch.texture] : 1;
        for (int tri = 0; tri < batch.vertexCount(); tri += 3) {
            for (int corner = 0; corner < 4; corner++) {
                int i = tri + Math.min(corner, 2);
                float[] p = RomModels.transform(m, batch.positions[i * 3], batch.positions[i * 3 + 1],
                        batch.positions[i * 3 + 2]);
                float nx = batch.normals[i * 3], ny = batch.normals[i * 3 + 1], nz = batch.normals[i * 3 + 2];
                // The node's matrix turns the normal into model space, then on into view space.
                VEC.set(nx * m[0] + ny * m[4] + nz * m[8], nx * m[1] + ny * m[5] + nz * m[9],
                        nx * m[2] + ny * m[6] + nz * m[10]);
                MODEL_TO_VIEW.transform(VEC);
                if (VEC.lengthSquared() > 0) VEC.normalize();
                int shade = shade(batch, i, VEC.x, VEC.y, VEC.z);
                float r = 1, g = 1, b = 1;
                if (!textured || batch.colorMode == RomModels.COLOR_MODULATE) {
                    r = (shade >>> 16 & 0xFF) / 255f;
                    g = (shade >>> 8 & 0xFF) / 255f;
                    b = (shade & 0xFF) / 255f;
                }
                float a = alpha * switch (batch.alphaMode) {
                    case RomModels.ALPHA_SHADE -> (shade >>> 24) / 255f;
                    case RomModels.ALPHA_CONSTANT -> constant / 255f;
                    default -> 1f;
                };
                float u = 0.5f, v = 0.5f;
                if (textured && batch.texgen) {
                    u = (VEC.x + 1) * batch.texgenU * uScale;
                    v = (-VEC.y + 1) * batch.texgenV * vScale;
                } else if (textured) {
                    u = batch.uvs[i * 2] * uScale;
                    v = batch.uvs[i * 2 + 1] * vScale;
                }
                out.addVertex(pose, p[0], p[1], p[2])
                        .setColor(r, g, b, a)
                        .setUv(u, v)
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, 0, 1, 0); // SM64's own shading is in the color; keep Minecraft's flat
            }
        }
    }

    /** The shade: SM64's lights in view space for lit batches, the vertex color otherwise. ARGB. */
    private static int shade(RomModels.Batch batch, int i, float vx, float vy, float vz) {
        if (!batch.lighting) {
            int c = batch.colors[i];
            return (c & 0xFF) << 24 | c >>> 8;
        }
        float d = Math.max(0, vx * batch.lightX + vy * batch.lightY + vz * batch.lightZ);
        int r = Math.min(255, (int) ((batch.ambient >>> 16 & 0xFF) + (batch.diffuse >>> 16 & 0xFF) * d));
        int g = Math.min(255, (int) ((batch.ambient >>> 8 & 0xFF) + (batch.diffuse >>> 8 & 0xFF) * d));
        int b = Math.min(255, (int) ((batch.ambient & 0xFF) + (batch.diffuse & 0xFF) * d));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The model's textures, uploaded on first use (with SM64's wrap modes), plus a white one for untextured parts. */
    private static Textures textures(RomModels.Model model) {
        Textures cached = TEXTURES.get(model.kind());
        if (cached != null) return cached;
        var manager = Minecraft.getInstance().getTextureManager();
        if (!whiteRegistered) {
            whiteRegistered = true;
            NativeImage white = new NativeImage(4, 4, false);
            white.fillRect(0, 0, 4, 4, 0xFFFFFFFF);
            manager.register(WHITE, ClientCompat.texture("Retro64 Plus white", white));
        }
        int n = model.textures().size();
        ResourceLocation[] normal = new ResourceLocation[n], opaque = new ResourceLocation[n];
        float[] uScale = new float[n], vScale = new float[n];
        String base = "dynamic/rom_model/" + model.kind().name().toLowerCase() + "_";
        for (int t = 0; t < n; t++) {
            RomModels.Texture texture = model.textures().get(t);
            normal[t] = ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, base + t);
            opaque[t] = ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, base + t + "_opaque");
            upload(manager, normal[t], texture, false);
            upload(manager, opaque[t], texture, true);
            uScale[t] = texture.wrapS() == RomModels.WRAP_MIRROR ? 0.5f : 1;
            vScale[t] = texture.wrapT() == RomModels.WRAP_MIRROR ? 0.5f : 1;
        }
        Textures textures = new Textures(normal, opaque, uScale, vScale);
        TEXTURES.put(model.kind(), textures);
        return textures;
    }

    private static void upload(net.minecraft.client.renderer.texture.TextureManager manager, ResourceLocation id,
            RomModels.Texture texture, boolean opaque) {
        boolean mirrorS = texture.wrapS() == RomModels.WRAP_MIRROR, mirrorT = texture.wrapT() == RomModels.WRAP_MIRROR;
        int w = texture.width(), h = texture.height();
        NativeImage image = new NativeImage(mirrorS ? 2 * w : w, mirrorT ? 2 * h : h, false);
        byte[] rgba = texture.rgba();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                // The mirrored half reads the texture backwards.
                int sx = x < w ? x : 2 * w - 1 - x, sy = y < h ? y : 2 * h - 1 - y;
                int i = 4 * (sx + sy * w);
                int a = opaque ? 0xFF : rgba[i + 3] & 0xFF;
                ClientCompat.setPixelAbgr(image, x, y, a << 24 | (rgba[i + 2] & 0xFF) << 16 | (rgba[i + 1] & 0xFF) << 8
                        | (rgba[i] & 0xFF));
            }
        }
        manager.register(id, ClientCompat.texture("Retro64 Plus " + id.getPath(), image,
                texture.wrapS() != RomModels.WRAP_CLAMP, texture.wrapT() != RomModels.WRAP_CLAMP));
    }
}
