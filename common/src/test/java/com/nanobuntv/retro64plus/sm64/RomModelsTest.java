package com.nanobuntv.retro64plus.sm64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * M4's ROM models (D19, D79) decoded from a real ROM: every model has triangles, textures and (where SM64 animates
 * it) animations, and the poses move. Each model is also drawn by a small software rasterizer into
 * {@code common/versions/1.21.1/build/rom-models/} (four frames of its first animation), to look at by eye.
 */
@EnabledIfSystemProperty(named = "retro64plus.testRom", matches = ".+")
class RomModelsTest {
    private static final int SIZE = 256;

    @Test
    void decodesEveryModel() throws Exception {
        Rom.Loaded rom = Rom.load(Path.of(System.getProperty("retro64plus.testRom")));
        List<Exception> errors = new ArrayList<>();
        Map<RomModels.Kind, RomModels.Model> models = RomModels.load(rom.data(), errors);
        for (Exception e : errors) e.printStackTrace();
        assertTrue(errors.isEmpty(), "models failed: " + errors);
        assertEquals(RomModels.Kind.values().length, models.size());

        Path out = Path.of("build", "rom-models");
        Files.createDirectories(out);
        for (RomModels.Model model : models.values()) {
            int[] triangles = {0};
            boolean[] texgen = {false};
            float[] bounds = bounds(model, null, 0, triangles, texgen);
            System.out.printf("%s: %d triangles, %d textures, %d animations, size %.0f x %.0f x %.0f (y %.0f to %.0f)%n",
                    model.kind(), triangles[0], model.textures().size(), model.animations().size(),
                    bounds[3] - bounds[0], bounds[4] - bounds[1], bounds[5] - bounds[2], bounds[1], bounds[4]);
            for (RomModels.Animation a : model.animations()) {
                float[] ab = bounds(model, a, 0, new int[1], new boolean[1]);
                System.out.printf("  animation flags 0x%02X divisor %d frames %d-%d loop %d: y %.0f to %.0f%n", a.flags,
                        a.yTransDivisor, a.startFrame, a.loopEnd, a.loopStart, ab[1], ab[4]);
            }
            assertTrue(triangles[0] >= 2, model.kind() + " has no triangles");
            boolean animated = model.kind().anims != 0;
            assertEquals(animated, !model.animations().isEmpty(), model.kind() + " animations");
            if (model.kind() != RomModels.Kind.CHAIN_LINK) {
                assertFalse(model.textures().isEmpty(), model.kind() + " has no textures");
            }
            for (RomModels.Texture t : model.textures()) {
                int opaque = 0;
                for (int i = 3; i < t.rgba().length; i += 4) if (t.rgba()[i] != 0) opaque++;
                assertTrue(opaque > 0, model.kind() + " has an empty texture");
            }
            if (model.kind() == RomModels.Kind.STAR) assertTrue(texgen[0], "the star's body isn't environment-mapped");
            if (animated) {
                RomModels.Animation walk = model.animations().get(0);
                float[] a = bounds(model, walk, 0, new int[1], new boolean[1]);
                float[] b = bounds(model, walk, (walk.loopEnd - walk.startFrame) / 2f, new int[1], new boolean[1]);
                assertFalse(Arrays.equals(a, b) && walk.loopEnd - walk.startFrame > 2, model.kind() + " doesn't move");
            }
            String name = model.kind().name().toLowerCase();
            ImageIO.write(draw(model, false), "png", out.resolve(name + ".png").toFile());
            ImageIO.write(draw(model, true), "png", out.resolve(name + "_turn.png").toFile());
            for (int i = 0; i < model.textures().size(); i++) {
                RomModels.Texture t = model.textures().get(i);
                BufferedImage image = new BufferedImage(t.width(), t.height(), BufferedImage.TYPE_INT_ARGB);
                for (int p = 0; p < t.width() * t.height(); p++) {
                    byte[] c = t.rgba();
                    image.setRGB(p % t.width(), p / t.width(), (c[4 * p + 3] & 0xFF) << 24 | (c[4 * p] & 0xFF) << 16
                            | (c[4 * p + 1] & 0xFF) << 8 | (c[4 * p + 2] & 0xFF));
                }
                ImageIO.write(image, "png", out.resolve(name + "_texture" + i + ".png").toFile());
            }
        }
    }

    /** min x, y, z, max x, y, z over every vertex in a pose (billboards facing +z). */
    private static float[] bounds(RomModels.Model model, RomModels.Animation anim, float frame, int[] triangles,
            boolean[] texgen) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE,
                -Float.MAX_VALUE};
        RomModels.pose(model, anim, frame, 0, new float[] {1, 0, 0, 0, 1, 0, 0, 0, 1}, (mesh, layer, m) -> {
            for (RomModels.Batch batch : mesh.batches()) {
                triangles[0] += batch.vertexCount() / 3;
                texgen[0] |= batch.texgen;
                for (int i = 0; i < batch.vertexCount(); i++) {
                    float[] p = RomModels.transform(m, batch.positions[i * 3], batch.positions[i * 3 + 1],
                            batch.positions[i * 3 + 2]);
                    for (int k = 0; k < 3; k++) {
                        b[k] = Math.min(b[k], p[k]);
                        b[k + 3] = Math.max(b[k + 3], p[k]);
                    }
                }
            }
        });
        return b;
    }

    /**
     * Four pictures side by side: frames through the first animation seen from the front-left and a little above, or
     * ({@code turn}) the first frame from the front, the right, the back and the left.
     */
    private static BufferedImage draw(RomModels.Model model, boolean turn) {
        BufferedImage image = new BufferedImage(SIZE * 4, SIZE, BufferedImage.TYPE_INT_ARGB);
        RomModels.Animation anim = model.animations().isEmpty() ? null : model.animations().get(0);
        float[] bounds = bounds(model, anim, 0, new int[1], new boolean[1]);
        float extent = 0;
        for (int k = 0; k < 3; k++) extent = Math.max(extent, bounds[k + 3] - bounds[k]);
        float cx = (bounds[0] + bounds[3]) / 2, cy = (bounds[1] + bounds[4]) / 2, cz = (bounds[2] + bounds[5]) / 2;
        float scale = SIZE * 0.8f / Math.max(1, extent);
        for (int f = 0; f < 4; f++) {
            double yaw = Math.toRadians(turn ? 90 * f : 35), pitch = Math.toRadians(turn ? 10 : 20);
            // Camera axes in model space: right, up, back (towards the viewer).
            float[] right = {(float) Math.cos(yaw), 0, (float) -Math.sin(yaw)};
            float[] back = {(float) (Math.sin(yaw) * Math.cos(pitch)), (float) Math.sin(pitch),
                    (float) (Math.cos(yaw) * Math.cos(pitch))};
            float[] up = cross(back, right);
            float[] billboard = {right[0], right[1], right[2], up[0], up[1], up[2], back[0], back[1], back[2]};
            float frame = anim == null || turn ? 0 : f * Math.max(1, anim.loopEnd - anim.startFrame) / 4f;
            float[] depth = new float[SIZE * SIZE];
            Arrays.fill(depth, -Float.MAX_VALUE);
            int ox = f * SIZE;
            RomModels.pose(model, anim, frame, 0, billboard, (mesh, layer, m) -> {
                for (RomModels.Batch batch : mesh.batches()) {
                    RomModels.Texture tex = batch.texture >= 0 ? model.textures().get(batch.texture) : null;
                    for (int t = 0; t < batch.vertexCount(); t += 3) {
                        float[][] screen = new float[3][];
                        int[] colors = new int[3];
                        float[][] uv = new float[3][];
                        for (int k = 0; k < 3; k++) {
                            int i = t + k;
                            float[] p = RomModels.transform(m, batch.positions[i * 3], batch.positions[i * 3 + 1],
                                    batch.positions[i * 3 + 2]);
                            float x = p[0] - cx, y = p[1] - cy, z = p[2] - cz;
                            screen[k] = new float[] {SIZE / 2f + dot(right, x, y, z) * scale,
                                    SIZE / 2f - dot(up, x, y, z) * scale, dot(back, x, y, z)};
                            float[] n = normal(m, batch, i);
                            float vx = dot(right, n[0], n[1], n[2]), vy = dot(up, n[0], n[1], n[2]),
                                    vz = dot(back, n[0], n[1], n[2]);
                            colors[k] = shade(batch, i, vx, vy, vz);
                            uv[k] = batch.texgen
                                    ? new float[] {(vx + 1) * batch.texgenU, (-vy + 1) * batch.texgenV}
                                    : new float[] {batch.uvs[i * 2], batch.uvs[i * 2 + 1]};
                        }
                        raster(image, depth, ox, screen, colors, uv, tex, batch);
                    }
                }
            });
        }
        return image;
    }

    private static float[] normal(float[] m, RomModels.Batch batch, int i) {
        float x = batch.normals[i * 3], y = batch.normals[i * 3 + 1], z = batch.normals[i * 3 + 2];
        float nx = x * m[0] + y * m[4] + z * m[8], ny = x * m[1] + y * m[5] + z * m[9], nz = x * m[2] + y * m[6] + z * m[10];
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return length == 0 ? new float[] {0, 1, 0} : new float[] {nx / length, ny / length, nz / length};
    }

    /** The shade: SM64's lights in view space for lit batches, the vertex color otherwise. ARGB. */
    static int shade(RomModels.Batch batch, int i, float vx, float vy, float vz) {
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

    private static void raster(BufferedImage image, float[] depth, int ox, float[][] p, int[] colors, float[][] uv,
            RomModels.Texture tex, RomModels.Batch batch) {
        float area = (p[1][0] - p[0][0]) * (p[2][1] - p[0][1]) - (p[2][0] - p[0][0]) * (p[1][1] - p[0][1]);
        if (Math.abs(area) < 1e-6) return;
        if (batch.cullBack && area > 0) return; // screen y points down: counter-clockwise faces have negative area
        int minX = Math.max(0, (int) Math.floor(Math.min(p[0][0], Math.min(p[1][0], p[2][0]))));
        int maxX = Math.min(SIZE - 1, (int) Math.ceil(Math.max(p[0][0], Math.max(p[1][0], p[2][0]))));
        int minY = Math.max(0, (int) Math.floor(Math.min(p[0][1], Math.min(p[1][1], p[2][1]))));
        int maxY = Math.min(SIZE - 1, (int) Math.ceil(Math.max(p[0][1], Math.max(p[1][1], p[2][1]))));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f, py = y + 0.5f;
                float w0 = ((p[1][0] - px) * (p[2][1] - py) - (p[2][0] - px) * (p[1][1] - py)) / area;
                float w1 = ((p[2][0] - px) * (p[0][1] - py) - (p[0][0] - px) * (p[2][1] - py)) / area;
                float w2 = 1 - w0 - w1;
                if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                float z = w0 * p[0][2] + w1 * p[1][2] + w2 * p[2][2];
                if (z <= depth[y * SIZE + x]) continue;
                int[] c = new int[4];
                for (int k = 0; k < 4; k++) {
                    int shift = 24 - 8 * k;
                    c[k] = (int) (w0 * (colors[0] >>> shift & 0xFF) + w1 * (colors[1] >>> shift & 0xFF)
                            + w2 * (colors[2] >>> shift & 0xFF));
                }
                int texel = 0xFFFFFFFF;
                if (tex != null) {
                    float u = w0 * uv[0][0] + w1 * uv[1][0] + w2 * uv[2][0];
                    float v = w0 * uv[0][1] + w1 * uv[1][1] + w2 * uv[2][1];
                    int tx = wrap((int) Math.floor(u * tex.width()), tex.width(), tex.wrapS());
                    int ty = wrap((int) Math.floor(v * tex.height()), tex.height(), tex.wrapT());
                    int at = 4 * (tx + ty * tex.width());
                    byte[] rgba = tex.rgba();
                    texel = (rgba[at + 3] & 0xFF) << 24 | (rgba[at] & 0xFF) << 16 | (rgba[at + 1] & 0xFF) << 8
                            | (rgba[at + 2] & 0xFF);
                }
                int a = switch (batch.alphaMode) {
                    case RomModels.ALPHA_TEXTURE -> texel >>> 24;
                    case RomModels.ALPHA_SHADE -> c[0];
                    default -> batch.constantAlpha;
                };
                if (a < 128) continue;
                int[] out = new int[3];
                for (int k = 0; k < 3; k++) {
                    int t = texel >>> (16 - 8 * k) & 0xFF, s = c[k + 1];
                    out[k] = switch (batch.colorMode) {
                        case RomModels.COLOR_TEXTURE -> tex == null ? 255 : t;
                        case RomModels.COLOR_MODULATE -> tex == null ? s : t * s / 255;
                        default -> s;
                    };
                }
                depth[y * SIZE + x] = z;
                image.setRGB(ox + x, y, 0xFF000000 | out[0] << 16 | out[1] << 8 | out[2]);
            }
        }
    }

    private static int wrap(int i, int size, int mode) {
        return switch (mode) {
            case RomModels.WRAP_CLAMP -> Math.max(0, Math.min(size - 1, i));
            case RomModels.WRAP_MIRROR -> {
                int period = Math.floorMod(i, 2 * size);
                yield period < size ? period : 2 * size - 1 - period;
            }
            default -> Math.floorMod(i, size);
        };
    }

    private static float dot(float[] a, float x, float y, float z) {
        return a[0] * x + a[1] * y + a[2] * z;
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
