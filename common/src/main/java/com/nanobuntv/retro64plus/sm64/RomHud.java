package com.nanobuntv.retro64plus.sm64;

/**
 * HUD textures taken from the player's ROM (M2: SM64's power meter, D45; M3: the coin and the HUD's digits, D57). Decoded here in Java: the native library
 * only extracts Mario's own textures. Plain Java, so the tests can check it against a real ROM.
 */
public final class RomHud {
    /**
     * Segment 3 of the US ROM (common actor data), MIO0-compressed. The power meter's textures sit in it at the
     * offsets of their segmented addresses in the decompilation (actors/power_meter/model.inc.c: 0x030233E0 and on).
     */
    static final int SEGMENT3_ROM_OFFSET = 0x201410;
    private static final int LEFT = 0x233E0, RIGHT = 0x243E0, FULL = 0x253E0, SEGMENT_STEP = 0x800;

    /** An RGBA image, rows top to bottom. */
    public record Image(int width, int height, byte[] rgba) {
        public int argb(int x, int y) {
            int i = 4 * (x + y * width);
            return (rgba[i + 3] & 0xFF) << 24 | (rgba[i] & 0xFF) << 16 | (rgba[i + 1] & 0xFF) << 8 | (rgba[i + 2] & 0xFF);
        }
    }

    /**
     * The meter's two halves (32 × 64 each, the "POWER" lettering and the rim) and its middle for 1 to 8 segments
     * ({@code segments[n - 1]}, 32 × 32).
     */
    public record PowerMeter(Image left, Image right, Image[] segments) {}

    /**
     * Segment 2 of the US ROM (the HUD font and other main textures), MIO0-compressed. Its 16 × 16 RGBA16 glyphs start
     * at 0 (bin/segment2.c, texture_hud_char_0 and on), 0x200 bytes each: the digits, then the letters the US version
     * uses, then "×" (33), the coin (34), Mario's head (35) and the star (36).
     */
    static final int SEGMENT2_ROM_OFFSET = 0x108A40;
    private static final int GLYPH = 0x200, GLYPH_TIMES = 33, GLYPH_COIN = 34, GLYPH_STAR = 36;
    /**
     * The coin's four spin frames (actors/coin/model.inc.c, 0x03005780 and on: front, tilted, edge-on, tilted the
     * other way), 32 × 32 IA16, in segment 3.
     */
    private static final int COIN_FRAMES = 0x5780, COIN_FRAME_STEP = 0x800;

    /**
     * Coin art (M3, D50, D57): the spinning coin's frames (grey, SM64 tints them yellow), and the HUD's digits 0–9,
     * "×", coin and star glyphs.
     */
    public record CoinArt(Image[] spin, Image[] digits, Image times, Image coin, Image star) {}

    private RomHud() {}

    public static CoinArt coinArt(byte[] rom) {
        byte[] segment3 = mio0(rom, SEGMENT3_ROM_OFFSET);
        Image[] spin = new Image[4];
        for (int i = 0; i < 4; i++) spin[i] = ia16(segment3, COIN_FRAMES + i * COIN_FRAME_STEP, 32, 32);
        byte[] segment2 = mio0(rom, SEGMENT2_ROM_OFFSET);
        Image[] digits = new Image[10];
        for (int i = 0; i < 10; i++) digits[i] = rgba16(segment2, i * GLYPH, 16, 16);
        return new CoinArt(spin, digits, rgba16(segment2, GLYPH_TIMES * GLYPH, 16, 16),
                rgba16(segment2, GLYPH_COIN * GLYPH, 16, 16), rgba16(segment2, GLYPH_STAR * GLYPH, 16, 16));
    }

    public static PowerMeter powerMeter(byte[] rom) {
        byte[] segment = mio0(rom, SEGMENT3_ROM_OFFSET);
        Image[] segments = new Image[8];
        for (int n = 1; n <= 8; n++) segments[n - 1] = rgba16(segment, FULL + (8 - n) * SEGMENT_STEP, 32, 32);
        return new PowerMeter(rgba16(segment, LEFT, 32, 64), rgba16(segment, RIGHT, 32, 64), segments);
    }

    /** Decompresses the MIO0 block at {@code offset} (Nintendo's LZ format: a bit stream, back-references, raw bytes). */
    static byte[] mio0(byte[] data, int offset) {
        if (data[offset] != 'M' || data[offset + 1] != 'I' || data[offset + 2] != 'O' || data[offset + 3] != '0') {
            throw new IllegalArgumentException("no MIO0 block at 0x" + Integer.toHexString(offset));
        }
        int size = int32(data, offset + 4);
        int comp = offset + int32(data, offset + 8);
        int raw = offset + int32(data, offset + 12);
        int bitsAt = offset + 16;
        byte[] out = new byte[size];
        int n = 0, bits = 0, left = 0;
        while (n < size) {
            if (left == 0) {
                bits = int32(data, bitsAt);
                bitsAt += 4;
                left = 32;
            }
            if (bits < 0) { // top bit: a raw byte
                out[n++] = data[raw++];
            } else {
                int v = (data[comp] & 0xFF) << 8 | (data[comp + 1] & 0xFF);
                comp += 2;
                int length = (v >>> 12) + 3, distance = (v & 0xFFF) + 1;
                for (int i = 0; i < length && n < size; i++, n++) out[n] = out[n - distance];
            }
            bits <<= 1;
            left--;
        }
        return out;
    }

    /** An N64 RGBA16 texture (5-5-5-1, big-endian) as RGBA. */
    static Image rgba16(byte[] data, int offset, int width, int height) {
        byte[] rgba = new byte[width * height * 4];
        for (int i = 0; i < width * height; i++) {
            int v = (data[offset + 2 * i] & 0xFF) << 8 | (data[offset + 2 * i + 1] & 0xFF);
            rgba[4 * i] = (byte) ((v >>> 11 & 31) * 255 / 31);
            rgba[4 * i + 1] = (byte) ((v >>> 6 & 31) * 255 / 31);
            rgba[4 * i + 2] = (byte) ((v >>> 1 & 31) * 255 / 31);
            rgba[4 * i + 3] = (byte) ((v & 1) != 0 ? 255 : 0);
        }
        return new Image(width, height, rgba);
    }

    /** An N64 IA16 texture (8-bit intensity, 8-bit alpha) as RGBA, grey. */
    static Image ia16(byte[] data, int offset, int width, int height) {
        byte[] rgba = new byte[width * height * 4];
        for (int i = 0; i < width * height; i++) {
            byte intensity = data[offset + 2 * i];
            rgba[4 * i] = rgba[4 * i + 1] = rgba[4 * i + 2] = intensity;
            rgba[4 * i + 3] = data[offset + 2 * i + 1];
        }
        return new Image(width, height, rgba);
    }

    private static int int32(byte[] data, int at) {
        return (data[at] & 0xFF) << 24 | (data[at + 1] & 0xFF) << 16 | (data[at + 2] & 0xFF) << 8 | (data[at + 3] & 0xFF);
    }
}
