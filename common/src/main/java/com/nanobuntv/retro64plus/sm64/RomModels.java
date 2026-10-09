package com.nanobuntv.retro64plus.sm64;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * SM64 object models taken from the player's ROM (M4, D19, D79): geo layouts, Fast3D display lists, textures and
 * animations, decoded in Java like {@link RomHud} (the native library only knows Mario). Plain Java, so the tests can
 * check it against a real ROM and draw it without Minecraft.
 *
 * <p>The US ROM keeps object data in shared groups: compressed data segments (display lists, textures, animations)
 * and raw geo layout segments. Their ROM ranges come from the level scripts' load commands; the addresses inside them
 * from the decompilation (the {@code actors/} sources name each one).
 */
public final class RomModels {
    /** A ROM segment: its number, where it is, and whether it's MIO0-compressed. */
    private record Segment(int id, int start, int end, boolean mio0) {}

    private static final Segment[] SEGMENTS = {
            new Segment(0x03, 0x201410, 0x218DA0, true),  // common1: the star, the coin
            new Segment(0x16, 0x218DA0, 0x219E00, false), // common1's geo layouts
            new Segment(0x08, 0x1F2200, 0x2008D0, true),  // common0: Goomba, Bob-omb, Koopa shell
            new Segment(0x0F, 0x2008D0, 0x201410, false), // common0's geo layouts
            new Segment(0x06, 0x1C4230, 0x1D7C90, true),  // group14: Koopa, Chain Chomp, its post and chain
            new Segment(0x0D, 0x1D7C90, 0x1D8310, false), // group14's geo layouts
    };

    /** The models M4 uses: the geo layout and, for animated ones, the animation table (decomp addresses). */
    public enum Kind {
        STAR(0x16000EA0, 0),
        GOOMBA(0x0F0006E4, 0x0801DA4C),
        BOBOMB(0x0F0007B8, 0x0802396C),
        KOOPA_SHELL(0x0F000AB0, 0),
        KOOPA_WITH_SHELL(0x0D000214, 0x06011364),
        KOOPA_WITHOUT_SHELL(0x0D0000D0, 0x06011364),
        CHAIN_CHOMP(0x0D0005EC, 0x06025178),
        /** A flat sprite: SM64 turns the whole object to the camera (its behavior's BILLBOARD()). */
        CHAIN_LINK(0x0D0005D0, 0, true),
        WOODEN_POST(0x0D0000B8, 0);

        final int geo, anims;
        final boolean billboard;

        Kind(int geo, int anims) {
            this(geo, anims, false);
        }

        Kind(int geo, int anims, boolean billboard) {
            this.geo = geo;
            this.anims = anims;
            this.billboard = billboard;
        }
    }

    /** SM64's drawing layers (geo commands' layer byte). */
    public static final int LAYER_OPAQUE = 1, LAYER_ALPHA = 4, LAYER_TRANSPARENT = 5;
    /** How a texture repeats along one axis. */
    public static final int WRAP_REPEAT = 0, WRAP_MIRROR = 1, WRAP_CLAMP = 2;
    /** What the color combiner makes of a pixel: the texture alone, the texture times the shade, or the shade alone. */
    public static final int COLOR_TEXTURE = 0, COLOR_MODULATE = 1, COLOR_SHADE = 2;
    /** Where the alpha comes from: the texture (with an extra constant), or the shade. */
    public static final int ALPHA_TEXTURE = 0, ALPHA_SHADE = 1, ALPHA_CONSTANT = 2;

    /** A decoded texture: RGBA rows top to bottom, and how it wraps. */
    public record Texture(int width, int height, byte[] rgba, int wrapS, int wrapT) {}

    /**
     * Triangles drawn with one render state. Positions are in the node's space (SM64 units), three vertices per
     * triangle. Lit batches carry normals and their lights (applied in view space, as SM64 does); the others carry
     * vertex colors.
     */
    public static final class Batch {
        /** Index into {@link Model#textures()}, or -1 for none. */
        public final int texture;
        public final boolean lighting, texgen, cullBack;
        public final int colorMode, alphaMode;
        /** The alpha for {@link #ALPHA_CONSTANT}, 0–255. */
        public final int constantAlpha;
        /** Lights: ambient and diffuse RGB, and the diffuse light's direction (view space, unit length). */
        public final int ambient, diffuse;
        public final float lightX, lightY, lightZ;
        /** For {@link #texgen}: u = (view normal x + 1) × texgenU, v = (view normal y + 1) × texgenV. */
        public final float texgenU, texgenV;
        public final float[] positions, uvs, normals;
        /** RGBA per vertex (packed 0xRRGGBBAA) for unlit batches. */
        public final int[] colors;

        Batch(State s, int texture, List<float[]> verts) {
            this.texture = texture;
            this.lighting = s.lighting();
            this.texgen = s.texgen() && texture >= 0;
            this.cullBack = (s.geometryMode & G_CULL_BACK) != 0;
            this.colorMode = s.colorMode;
            this.alphaMode = s.alphaMode;
            this.constantAlpha = s.constantAlpha;
            this.ambient = s.lights[s.numLights] >>> 8;
            this.diffuse = s.lights[0] >>> 8;
            float lx = s.lightDir[0], ly = s.lightDir[1], lz = s.lightDir[2];
            float length = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
            if (length == 0) length = 1;
            this.lightX = lx / length;
            this.lightY = ly / length;
            this.lightZ = lz / length;
            // The RSP's texgen: s = (n · lookAt + 1) / 4 × the texture command's scale, in S10.5 texels.
            this.texgenU = s.texScaleS / 4f / 32f / Math.max(1, s.texWidth);
            this.texgenV = s.texScaleT / 4f / 32f / Math.max(1, s.texHeight);
            int n = verts.size();
            positions = new float[n * 3];
            uvs = new float[n * 2];
            normals = new float[n * 3];
            colors = new int[n];
            for (int i = 0; i < n; i++) {
                float[] v = verts.get(i);
                System.arraycopy(v, 0, positions, i * 3, 3);
                uvs[i * 2] = v[3];
                uvs[i * 2 + 1] = v[4];
                System.arraycopy(v, 5, normals, i * 3, 3);
                colors[i] = Float.floatToRawIntBits(v[8]);
            }
        }

        public int vertexCount() {
            return colors.length;
        }
    }

    /** A display list's triangles, in batches. */
    public record Mesh(List<Batch> batches) {}

    /** A geo layout node. */
    public static final class Node {
        public enum Type { GROUP, SCALE, ANIMATED_PART, BILLBOARD, SWITCH, DISPLAY_LIST }

        public final Type type;
        public final int layer;
        public final float x, y, z, scale;
        public final @Nullable Mesh mesh;
        public final List<Node> children = new ArrayList<>();

        Node(Type type, int layer, float x, float y, float z, float scale, @Nullable Mesh mesh) {
            this.type = type;
            this.layer = layer;
            this.x = x;
            this.y = y;
            this.z = z;
            this.scale = scale;
            this.mesh = mesh;
        }
    }

    /**
     * An SM64 animation: which frames it plays and loops, its translation kind, and the value and index tables (read
     * from the segment as they're needed).
     */
    public static final class Animation {
        static final int NOLOOP = 0x01, HOR_TRANS = 0x08, VERT_TRANS = 0x10, NO_TRANS = 0x40;

        public final int flags, yTransDivisor, startFrame, loopStart, loopEnd;
        private final byte[] data;
        private final int values, index;

        Animation(byte[] data, int at) {
            this.data = data;
            flags = s16(data, at);
            yTransDivisor = s16(data, at + 2);
            startFrame = s16(data, at + 4);
            loopStart = s16(data, at + 6);
            loopEnd = s16(data, at + 8);
            values = int32(data, at + 0x0C) & 0xFFFFFF;
            index = int32(data, at + 0x10) & 0xFFFFFF;
        }

        /** The frame shown after {@code steps} 30 Hz updates, as SM64 advances it (looping, or holding the last). */
        public int frameAfter(int steps) {
            int frame = startFrame + Math.max(0, steps);
            if (frame < loopEnd) return frame;
            if ((flags & NOLOOP) != 0 || loopEnd <= loopStart) return Math.max(loopStart, loopEnd - 1);
            return loopStart + (frame - loopEnd) % (loopEnd - loopStart);
        }

        /** The value of attribute {@code attribute} (a pair in the index table) at {@code frame}. */
        int value(int attribute, int frame) {
            int at = index + attribute * 4;
            if (at + 4 > data.length) return 0;
            int count = u16(data, at), offset = u16(data, at + 2);
            int i = offset + (frame < count ? frame : count - 1);
            int vat = values + i * 2;
            return count == 0 || vat < 0 || vat + 2 > data.length ? 0 : s16(data, vat);
        }
    }

    /** A decoded model: its geo tree, the textures its batches use, and its animations (empty if none). */
    public record Model(Kind kind, Node root, List<Texture> textures, List<Animation> animations) {}

    /** Receives each display list a pose draws, with the node's matrix (SM64's row-vector order). */
    public interface Visitor {
        void draw(Mesh mesh, int layer, float[] matrix);
    }

    private RomModels() {}

    /** Decodes every {@link Kind}; a model that fails is left out (and the cause logged by the caller). */
    public static Map<Kind, Model> load(byte[] rom, List<Exception> errors) {
        byte[][] segments = new byte[32][];
        for (Segment s : SEGMENTS) {
            segments[s.id] = s.mio0 ? RomHud.mio0(rom, s.start) : java.util.Arrays.copyOfRange(rom, s.start, s.end);
        }
        Map<Kind, Model> models = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            try {
                models.put(kind, new Decoder(segments).model(kind));
            } catch (RuntimeException e) {
                errors.add(new IllegalStateException("model " + kind, e));
            }
        }
        return models;
    }

    // ---- Posing ---------------------------------------------------------------------------------------------

    /**
     * Walks the model in a pose, as SM64's geo processor does: animated parts take their rotation (and the first its
     * translation) from {@code animation} at {@code frame} (fractional frames blend with the next), switch nodes show
     * child {@code animState}, billboards turn to {@code billboard} (three rows: right, up and back, in the model's
     * space; SM64 keeps only the object's own scale under one, which the caller applies).
     */
    public static void pose(Model model, @Nullable Animation animation, float frame, int animState, float[] billboard,
            Visitor visitor) {
        Poser poser = new Poser(animation, frame, animState, billboard, visitor);
        float[] start = !model.kind().billboard ? identity() : new float[] {
                billboard[0], billboard[1], billboard[2], 0,
                billboard[3], billboard[4], billboard[5], 0,
                billboard[6], billboard[7], billboard[8], 0,
                0, 0, 0, 1};
        poser.walk(model.root, start);
    }

    private static final class Poser {
        private final @Nullable Animation anim;
        private final int frameA, frameB;
        private final float blend;
        private final int animState;
        private final float[] billboard;
        private final Visitor visitor;
        private int attribute;
        private boolean firstPart = true;

        Poser(@Nullable Animation anim, float frame, int animState, float[] billboard, Visitor visitor) {
            this.anim = anim;
            int steps = (int) Math.floor(frame);
            this.frameA = anim == null ? 0 : anim.frameAfter(steps);
            this.frameB = anim == null ? 0 : anim.frameAfter(steps + 1);
            this.blend = frame - steps;
            this.animState = animState;
            this.billboard = billboard;
            this.visitor = visitor;
        }

        void walk(Node node, float[] parent) {
            float[] m = parent;
            switch (node.type) {
                case SCALE -> m = mul(scaling(node.scale), parent);
                case ANIMATED_PART -> m = mul(animated(node), parent);
                case BILLBOARD -> {
                    float[] origin = transform(parent, node.x, node.y, node.z);
                    m = new float[] {
                            billboard[0], billboard[1], billboard[2], 0,
                            billboard[3], billboard[4], billboard[5], 0,
                            billboard[6], billboard[7], billboard[8], 0,
                            origin[0], origin[1], origin[2], 1};
                }
                default -> {}
            }
            if (node.mesh != null) visitor.draw(node.mesh, node.layer, m);
            if (node.type == Node.Type.SWITCH) {
                if (!node.children.isEmpty()) {
                    int pick = animState >= 0 && animState < node.children.size() ? animState : 0;
                    walk(node.children.get(pick), m);
                }
                return;
            }
            for (Node child : node.children) walk(child, m);
        }

        private float[] animated(Node node) {
            float tx = node.x, ty = node.y, tz = node.z;
            int rx = 0, ry = 0, rz = 0;
            if (anim != null) {
                if (firstPart) {
                    firstPart = false;
                    float multiplier = anim.yTransDivisor == 0 ? 1 : 0; // objects' animYTrans is 0
                    if ((anim.flags & Animation.HOR_TRANS) != 0) { // only vertical translation
                        attribute++;
                        ty += lerp(attribute++) * multiplier;
                        attribute++;
                    } else if ((anim.flags & Animation.VERT_TRANS) != 0) { // only lateral translation
                        tx += lerp(attribute++) * multiplier;
                        attribute++;
                        tz += lerp(attribute++) * multiplier;
                    } else if ((anim.flags & Animation.NO_TRANS) != 0) {
                        attribute += 3;
                    } else {
                        tx += lerp(attribute++) * multiplier;
                        ty += lerp(attribute++) * multiplier;
                        tz += lerp(attribute++) * multiplier;
                    }
                }
                rx = angle(attribute++);
                ry = angle(attribute++);
                rz = angle(attribute++);
            }
            return rotateXyzTranslate(tx, ty, tz, rx, ry, rz);
        }

        private float lerp(int attribute) {
            int a = anim.value(attribute, frameA), b = anim.value(attribute, frameB);
            return a + (b - a) * blend;
        }

        /** An angle blended the short way round. */
        private int angle(int attribute) {
            int a = anim.value(attribute, frameA), b = anim.value(attribute, frameB);
            int delta = (short) (b - a);
            return Math.round(a + delta * blend);
        }
    }

    // ---- Matrices (SM64's: row vectors, child = local × parent) ------------------------------------------------

    public static float[] identity() {
        return new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    private static float[] scaling(float s) {
        return new float[] {s, 0, 0, 0, 0, s, 0, 0, 0, 0, s, 0, 0, 0, 0, 1};
    }

    /** SM64's {@code mtxf_rotate_xyz_and_translate}. */
    static float[] rotateXyzTranslate(float x, float y, float z, int rx, int ry, int rz) {
        float sx = sins(rx), cx = coss(rx), sy = sins(ry), cy = coss(ry), sz = sins(rz), cz = coss(rz);
        return new float[] {
                cy * cz, cy * sz, -sy, 0,
                sx * sy * cz - cx * sz, sx * sy * sz + cx * cz, sx * cy, 0,
                cx * sy * cz + sx * sz, cx * sy * sz - sx * cz, cx * cy, 0,
                x, y, z, 1};
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) sum += a[i * 4 + k] * b[k * 4 + j];
                r[i * 4 + j] = sum;
            }
        }
        return r;
    }

    /** A point through a matrix. */
    public static float[] transform(float[] m, float x, float y, float z) {
        return new float[] {
                x * m[0] + y * m[4] + z * m[8] + m[12],
                x * m[1] + y * m[5] + z * m[9] + m[13],
                x * m[2] + y * m[6] + z * m[10] + m[14]};
    }

    private static float sins(int angle) {
        return (float) Math.sin(angle * Math.PI / 32768);
    }

    private static float coss(int angle) {
        return (float) Math.cos(angle * Math.PI / 32768);
    }

    // ---- Decoding -------------------------------------------------------------------------------------------

    private static final int G_CULL_BACK = 0x2000, G_LIGHTING = 0x20000, G_TEXTURE_GEN = 0x40000;

    /** The RSP and RDP state a display list builds up. */
    private static final class State {
        int geometryMode = G_CULL_BACK | G_LIGHTING;
        boolean textureOn;
        int texScaleS = 0xFFFF, texScaleT = 0xFFFF;
        int numLights = 1;
        /** Light colors (0xRRGGBB00): diffuse lights first, the ambient one after them. */
        final int[] lights = new int[8];
        final float[] lightDir = {0x28, 0x28, 0x28};
        int colorMode = COLOR_SHADE, alphaMode = ALPHA_SHADE, constantAlpha = 255;
        int env = 0xFFFFFFFF, prim = 0xFFFFFFFF;
        int combine0, combine1;
        int timgAddr, timgFmt, timgSiz;
        /** Tile descriptors: format, size, tmem, cms, cmt, masks, maskt; and tile sizes. */
        final int[][] tiles = new int[8][7];
        final int[][] tileSizes = new int[8][4];
        final Map<Integer, int[]> tmem = new HashMap<>();
        int texWidth = 32, texHeight = 32;

        boolean lighting() {
            return (geometryMode & G_LIGHTING) != 0;
        }

        boolean texgen() {
            return (geometryMode & G_TEXTURE_GEN) != 0;
        }
    }

    private static final class Decoder {
        private final byte[][] segments;
        private final List<Texture> textures = new ArrayList<>();
        private final Map<Long, Integer> textureIds = new HashMap<>();
        private final Map<Integer, Mesh> meshes = new HashMap<>();

        Decoder(byte[][] segments) {
            this.segments = segments;
        }

        Model model(Kind kind) {
            Node root = geo(kind.geo);
            List<Animation> animations = new ArrayList<>();
            if (kind.anims != 0) {
                byte[] data = segment(kind.anims);
                for (int at = kind.anims & 0xFFFFFF; at + 4 <= data.length; at += 4) {
                    int pointer = int32(data, at);
                    if (pointer == 0 || pointer >>> 24 != kind.anims >>> 24) break;
                    animations.add(new Animation(data, pointer & 0xFFFFFF));
                }
            }
            return new Model(kind, root, List.copyOf(textures), List.copyOf(animations));
        }

        // -- geo layouts --

        private Node geo(int address) {
            byte[] data = segment(address);
            int at = address & 0xFFFFFF;
            Node root = new Node(Node.Type.GROUP, 0, 0, 0, 0, 1, null);
            // SM64's geo_layout.c: the last node made at each depth; a new node goes under the last one a level up.
            Node[] levels = new Node[64];
            levels[0] = root;
            int depth = 1;
            for (int guard = 0; guard < 10_000; guard++) {
                int cmd = data[at] & 0xFF, b1 = data[at + 1] & 0xFF;
                Node created = null;
                switch (cmd) {
                    case 0x01 -> { return root; } // end
                    case 0x04 -> { // open
                        if (depth + 1 >= levels.length) throw new IllegalStateException("geo layout too deep");
                        levels[depth + 1] = levels[depth];
                        depth++;
                        at += 4;
                        continue;
                    }
                    case 0x05 -> { depth = Math.max(1, depth - 1); at += 4; continue; } // close
                    case 0x0B -> { created = new Node(Node.Type.GROUP, 0, 0, 0, 0, 1, null); at += 4; } // start
                    case 0x0E -> { created = new Node(Node.Type.SWITCH, 0, 0, 0, 0, 1, null); at += 8; } // switch case
                    case 0x13 -> { // animated part
                        int dl = int32(data, at + 8);
                        created = new Node(Node.Type.ANIMATED_PART, b1, s16(data, at + 2), s16(data, at + 4),
                                s16(data, at + 6), 1, dl == 0 ? null : mesh(dl));
                        at += 12;
                    }
                    case 0x14 -> { // billboard
                        boolean withDl = (b1 & 0x80) != 0;
                        created = new Node(Node.Type.BILLBOARD, b1 & 0x7F, s16(data, at + 2), s16(data, at + 4),
                                s16(data, at + 6), 1, withDl ? mesh(int32(data, at + 8)) : null);
                        at += withDl ? 12 : 8;
                    }
                    case 0x15 -> { // display list
                        created = new Node(Node.Type.DISPLAY_LIST, b1, 0, 0, 0, 1, mesh(int32(data, at + 4)));
                        at += 8;
                    }
                    case 0x16 -> at += 8;  // shadow: Minecraft draws its own
                    case 0x18, 0x19 -> at += 8; // asm / background functions: not needed by these models
                    case 0x1D -> { // scale
                        boolean withDl = (b1 & 0x80) != 0;
                        created = new Node(Node.Type.SCALE, b1 & 0x7F, 0, 0, 0, int32(data, at + 4) / 65536f,
                                withDl ? mesh(int32(data, at + 8)) : null);
                        at += withDl ? 12 : 8;
                    }
                    case 0x20 -> at += 4;  // culling radius
                    default -> throw new IllegalStateException(String.format("geo command 0x%02X at 0x%08X", cmd,
                            (address & 0xFF000000) | at));
                }
                if (created == null) {
                    // A node Minecraft doesn't need still gets a place in the tree, so its children open under it.
                    created = new Node(Node.Type.GROUP, 0, 0, 0, 0, 1, null);
                }
                Node parent = levels[depth - 1] != null ? levels[depth - 1] : root;
                parent.children.add(created);
                levels[depth] = created;
            }
            throw new IllegalStateException("geo layout without an end");
        }

        // -- display lists --

        private Mesh mesh(int address) {
            Mesh cached = meshes.get(address);
            if (cached != null) return cached;
            MeshBuilder builder = new MeshBuilder();
            run(address, new State(), builder, 0);
            Mesh mesh = new Mesh(List.copyOf(builder.batches));
            meshes.put(address, mesh);
            return mesh;
        }

        private final class MeshBuilder {
            final List<Batch> batches = new ArrayList<>();
            final float[][] vertices = new float[16][];
            State batchState;
            int batchTexture = Integer.MIN_VALUE;
            long batchKey;
            List<float[]> current = new ArrayList<>();

            void flush() {
                if (!current.isEmpty()) batches.add(new Batch(batchState, batchTexture, current));
                current = new ArrayList<>();
            }
        }

        private void run(int address, State s, MeshBuilder out, int depth) {
            if (depth > 16) throw new IllegalStateException("display lists nested too deep");
            byte[] data = segment(address);
            int at = address & 0xFFFFFF;
            for (int guard = 0; guard < 100_000; guard++, at += 8) {
                int w0 = int32(data, at), w1 = int32(data, at + 4);
                int cmd = w0 >>> 24;
                switch (cmd) {
                    case 0x03 -> movemem(s, w0, w1);
                    case 0x04 -> vertices(s, out, w0, w1);
                    case 0x06 -> { // display list: call, or branch (bit 16)
                        run(w1, s, out, depth + 1);
                        if ((w0 >>> 16 & 0xFF) == 1) { out.flush(); return; }
                    }
                    case 0xB6 -> s.geometryMode &= ~w1;
                    case 0xB7 -> s.geometryMode |= w1;
                    case 0xB8 -> { if (depth == 0) out.flush(); return; } // end
                    case 0xBB -> { // texture
                        s.textureOn = (w0 & 0xFF) != 0;
                        s.texScaleS = w1 >>> 16;
                        s.texScaleT = w1 & 0xFFFF;
                    }
                    case 0xBC -> { // moveword: the light count
                        if ((w0 & 0xFF) == 0x02) s.numLights = Math.max(0, Math.min(6, (w1 - 0x80000000) / 32 - 1));
                    }
                    case 0xBF -> triangle(s, out, (w1 >>> 16 & 0xFF) / 10, (w1 >>> 8 & 0xFF) / 10, (w1 & 0xFF) / 10);
                    case 0xF2 -> { // tile size
                        int[] size = s.tileSizes[w1 >>> 24 & 7];
                        size[0] = w0 >>> 12 & 0xFFF;
                        size[1] = w0 & 0xFFF;
                        size[2] = w1 >>> 12 & 0xFFF;
                        size[3] = w1 & 0xFFF;
                    }
                    case 0xF3 -> { // load block: the image goes into the load tile's TMEM
                        int[] tile = s.tiles[w1 >>> 24 & 7];
                        s.tmem.put(tile[2], new int[] {s.timgAddr, s.timgFmt, s.timgSiz});
                    }
                    case 0xF5 -> { // tile
                        int[] tile = s.tiles[w1 >>> 24 & 7];
                        tile[0] = w0 >>> 21 & 7;
                        tile[1] = w0 >>> 19 & 3;
                        tile[2] = w0 & 0x1FF;
                        tile[3] = w1 >>> 8 & 3;
                        tile[4] = w1 >>> 18 & 3;
                        tile[5] = w1 >>> 4 & 0xF;
                        tile[6] = w1 >>> 14 & 0xF;
                    }
                    case 0xFA -> s.prim = w1;
                    case 0xFB -> s.env = w1;
                    case 0xFC -> combine(s, w0, w1);
                    case 0xFD -> { // texture image
                        s.timgFmt = w0 >>> 21 & 7;
                        s.timgSiz = w0 >>> 19 & 3;
                        s.timgAddr = w1;
                    }
                    default -> {} // syncs, other modes, scissor, fog: nothing to keep
                }
            }
            throw new IllegalStateException("display list without an end");
        }

        private void movemem(State s, int w0, int w1) {
            int index = w0 >>> 16 & 0xFF;
            if (index < 0x86 || index > 0x94) return; // only lights
            int light = (index - 0x86) / 2;
            byte[] data = segment(w1);
            int at = w1 & 0xFFFFFF;
            s.lights[light] = (data[at] & 0xFF) << 24 | (data[at + 1] & 0xFF) << 16 | (data[at + 2] & 0xFF) << 8;
            if (light == 0) {
                s.lightDir[0] = data[at + 8];
                s.lightDir[1] = data[at + 9];
                s.lightDir[2] = data[at + 10];
            }
        }

        private void vertices(State s, MeshBuilder out, int w0, int w1) {
            int n = (w0 >>> 20 & 0xF) + 1, v0 = w0 >>> 16 & 0xF;
            byte[] data = segment(w1);
            int at = w1 & 0xFFFFFF;
            for (int i = 0; i < n && v0 + i < 16; i++, at += 16) {
                float[] v = new float[9];
                v[0] = s16(data, at);
                v[1] = s16(data, at + 2);
                v[2] = s16(data, at + 4);
                // Texture coordinates are S10.5 texels, scaled by the texture command; turned to 0–1 at the triangle.
                v[3] = s16(data, at + 8) * (s.texScaleS / 65536f) / 32f;
                v[4] = s16(data, at + 10) * (s.texScaleT / 65536f) / 32f;
                v[5] = data[at + 12];
                v[6] = data[at + 13];
                v[7] = data[at + 14];
                v[8] = Float.intBitsToFloat(int32(data, at + 12));
                out.vertices[v0 + i] = v;
            }
        }

        private void triangle(State s, MeshBuilder out, int a, int b, int c) {
            int texture = s.textureOn && usesTexture(s) ? texture(s) : -1;
            long key = stateKey(s, texture);
            if (out.batchState == null || key != out.batchKey || texture != out.batchTexture) {
                out.flush();
                out.batchState = snapshot(s);
                out.batchTexture = texture;
                out.batchKey = key;
            }
            for (int i : new int[] {a, b, c}) {
                float[] v = out.vertices[i];
                if (v == null) return;
                float[] copy = v.clone();
                if (texture >= 0) {
                    Texture t = textures.get(texture);
                    copy[3] /= t.width();
                    copy[4] /= t.height();
                }
                if (!s.lighting()) { // normals are colors then; keep them out of the normal slots
                    copy[5] = 0;
                    copy[6] = 1;
                    copy[7] = 0;
                } else {
                    float nx = copy[5], ny = copy[6], nz = copy[7];
                    float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (length > 0) {
                        copy[5] = nx / length;
                        copy[6] = ny / length;
                        copy[7] = nz / length;
                    }
                }
                out.current.add(copy);
            }
        }

        private static boolean usesTexture(State s) {
            return s.colorMode != COLOR_SHADE || s.alphaMode == ALPHA_TEXTURE;
        }

        private long stateKey(State s, int texture) {
            long key = s.geometryMode & (G_CULL_BACK | G_LIGHTING | G_TEXTURE_GEN);
            key = key * 31 + s.colorMode;
            key = key * 31 + s.alphaMode;
            key = key * 31 + s.constantAlpha;
            key = key * 31 + s.numLights;
            for (int l : s.lights) key = key * 31 + l;
            for (float d : s.lightDir) key = key * 31 + Float.floatToIntBits(d);
            key = key * 31 + s.texScaleS;
            key = key * 31 + s.texScaleT;
            return key * 31 + texture;
        }

        private static State snapshot(State s) {
            State c = new State();
            c.geometryMode = s.geometryMode;
            c.textureOn = s.textureOn;
            c.texScaleS = s.texScaleS;
            c.texScaleT = s.texScaleT;
            c.numLights = s.numLights;
            System.arraycopy(s.lights, 0, c.lights, 0, s.lights.length);
            System.arraycopy(s.lightDir, 0, c.lightDir, 0, 3);
            c.colorMode = s.colorMode;
            c.alphaMode = s.alphaMode;
            c.constantAlpha = s.constantAlpha;
            c.texWidth = s.texWidth;
            c.texHeight = s.texHeight;
            return c;
        }

        /**
         * Reads the color combiner (cycle 1) into one of the few forms the renderer draws: (A − B) × C + D with the
         * texture and the shade as the inputs that matter. Every model M4 uses has shade, decal, modulate and fade
         * modes.
         */
        private static void combine(State s, int w0, int w1) {
            s.combine0 = w0;
            s.combine1 = w1;
            int a = w0 >>> 20 & 0xF, c = w0 >>> 15 & 0x1F, b = w1 >>> 28 & 0xF, d = w1 >>> 15 & 7;
            int aa = w0 >>> 12 & 7, ac = w0 >>> 9 & 7, ab = w1 >>> 12 & 7, ad = w1 >>> 9 & 7;
            boolean texA = a == 1, texC = c == 1 || c == 8, texD = d == 1;
            boolean shadeA = a == 4, shadeC = c == 4, shadeD = d == 4;
            boolean product = b >= 8 || b == 7; // B is zero
            if (texD && !texA && !texC) {
                s.colorMode = COLOR_TEXTURE;
            } else if (product && ((texA && shadeC) || (shadeA && texC))) {
                s.colorMode = COLOR_MODULATE;
            } else if (texA || texC || texD) {
                s.colorMode = shadeA || shadeC || shadeD ? COLOR_MODULATE : COLOR_TEXTURE;
            } else {
                s.colorMode = COLOR_SHADE;
            }
            boolean aTexture = aa == 1 || ac == 1 || ad == 1;
            boolean aShade = aa == 4 || ac == 4 || ad == 4;
            if (aTexture) {
                s.alphaMode = ALPHA_TEXTURE;
                s.constantAlpha = 255;
            } else if (aShade) {
                s.alphaMode = ALPHA_SHADE;
            } else {
                s.alphaMode = ALPHA_CONSTANT;
                int source = ad;
                s.constantAlpha = switch (source) {
                    case 3 -> s.prim & 0xFF;
                    case 5 -> s.env & 0xFF;
                    case 6 -> 255;
                    default -> 0;
                };
                if (ab != 7 || aa != 7) s.constantAlpha = 255; // anything fancier: draw it opaque
            }
        }

        /** The render tile's texture, decoded once per address and size. */
        private int texture(State s) {
            int[] tile = s.tiles[0];
            int[] size = s.tileSizes[0];
            int width = ((size[2] - size[0]) >> 2) + 1, height = ((size[3] - size[1]) >> 2) + 1;
            int[] loaded = s.tmem.get(tile[2]);
            if (loaded == null || width <= 0 || height <= 0 || width > 256 || height > 256) return -1;
            s.texWidth = width;
            s.texHeight = height;
            int fmt = tile[0], siz = tile[1];
            long key = (loaded[0] & 0xFFFFFFFFL) << 24 | (long) fmt << 21 | (long) siz << 19 | (long) width << 9 | height;
            Integer id = textureIds.get(key);
            if (id != null) return id;
            byte[] data = segment(loaded[0]);
            int at = loaded[0] & 0xFFFFFF;
            RomHud.Image image = switch (fmt << 2 | siz) {
                case 0 << 2 | 2 -> RomHud.rgba16(data, at, width, height); // RGBA16
                case 3 << 2 | 2 -> RomHud.ia16(data, at, width, height);   // IA16
                case 3 << 2 | 1 -> ia8(data, at, width, height);           // IA8
                case 4 << 2 | 1 -> i8(data, at, width, height);            // I8
                default -> null;
            };
            if (image == null) return -1;
            int wrapS = tile[5] == 0 || (tile[3] & 2) != 0 ? WRAP_CLAMP : (tile[3] & 1) != 0 ? WRAP_MIRROR : WRAP_REPEAT;
            int wrapT = tile[6] == 0 || (tile[4] & 2) != 0 ? WRAP_CLAMP : (tile[4] & 1) != 0 ? WRAP_MIRROR : WRAP_REPEAT;
            textures.add(new Texture(width, height, image.rgba(), wrapS, wrapT));
            textureIds.put(key, textures.size() - 1);
            return textures.size() - 1;
        }

        private byte[] segment(int address) {
            int id = address >>> 24 & 0x1F;
            byte[] data = segments[id];
            if (data == null) throw new IllegalStateException(String.format("segment 0x%02X isn't loaded (0x%08X)",
                    id, address));
            if ((address & 0xFFFFFF) >= data.length) throw new IllegalStateException(String.format(
                    "0x%08X is past segment 0x%02X's end", address, id));
            return data;
        }
    }

    private static RomHud.Image ia8(byte[] data, int offset, int width, int height) {
        byte[] rgba = new byte[width * height * 4];
        for (int i = 0; i < width * height; i++) {
            int v = data[offset + i] & 0xFF;
            byte intensity = (byte) ((v >>> 4) * 17);
            rgba[4 * i] = rgba[4 * i + 1] = rgba[4 * i + 2] = intensity;
            rgba[4 * i + 3] = (byte) ((v & 0xF) * 17);
        }
        return new RomHud.Image(width, height, rgba);
    }

    private static RomHud.Image i8(byte[] data, int offset, int width, int height) {
        byte[] rgba = new byte[width * height * 4];
        for (int i = 0; i < width * height; i++) {
            byte intensity = data[offset + i];
            rgba[4 * i] = rgba[4 * i + 1] = rgba[4 * i + 2] = rgba[4 * i + 3] = intensity;
        }
        return new RomHud.Image(width, height, rgba);
    }

    private static int s16(byte[] data, int at) {
        return (short) ((data[at] & 0xFF) << 8 | (data[at + 1] & 0xFF));
    }

    private static int u16(byte[] data, int at) {
        return (data[at] & 0xFF) << 8 | (data[at + 1] & 0xFF);
    }

    private static int int32(byte[] data, int at) {
        return (data[at] & 0xFF) << 24 | (data[at + 1] & 0xFF) << 16 | (data[at + 2] & 0xFF) << 8 | (data[at + 3] & 0xFF);
    }
}
