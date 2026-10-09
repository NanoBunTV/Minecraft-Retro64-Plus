package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import com.nanobuntv.retro64plus.sm64.lib.Libsm64;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Mario's texture, built from the atlas libsm64 extracted from the ROM. libsm64 meant it to be drawn by mixing texture
 * and vertex colour by the texture's alpha; Minecraft's entity shader only multiplies the two. So, as upstream did, each
 * tile gets the colour of the surface it's drawn on behind it (skin behind the eyes, red behind the cap's "M", blue
 * behind the buttons), and textured triangles are drawn with a white vertex colour. Untextured triangles (UV 1,1 from
 * libsm64) sample a white strip added after the atlas, so their vertex colour shows unchanged.
 *
 * <p>Luigi (D33) gets a second copy: green behind the cap logo, whose "M" is replaced by an "L" drawn here, and navy
 * behind the buttons; {@link #luigiColor} recolours the untextured triangles to match.
 */
final class MarioTexture {
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "dynamic/mario");
    static final ResourceLocation LUIGI_ID = ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "dynamic/luigi");
    /** Atlas width plus an 8-texel white strip. */
    static final int WIDTH = Libsm64.TEXTURE_WIDTH + 8;
    static final int HEIGHT = Libsm64.TEXTURE_HEIGHT;
    /** Where untextured triangles sample: the centre of a texel in the white strip. */
    static final float WHITE_U = (Libsm64.TEXTURE_WIDTH + 4.5f) / WIDTH;
    static final float WHITE_V = 0.5f / HEIGHT;
    /** Scales libsm64's atlas UVs (0..1 over the atlas) into this wider texture. */
    static final float U_SCALE = (float) Libsm64.TEXTURE_WIDTH / WIDTH;

    /** The cap logo's tile (atlas slot 2, 32 × 32: {@code mario_texture_m_logo} in load_tex_data.h). */
    private static final int LOGO_X = 128, LOGO_SIZE = 32;
    /** Luigi's colours, as NativeImage's ABGR and as RGB fractions. */
    private static final int LUIGI_GREEN_ABGR = 0xFF14A800, LUIGI_NAVY_ABGR = 0xFF8C2926;
    private static final float[] LUIGI_GREEN = {0.0f, 0.66f, 0.08f}, LUIGI_NAVY = {0.15f, 0.16f, 0.55f};

    private static boolean registered;

    private MarioTexture() {}

    /** Registers both textures once libsm64 is ready (render thread). */
    static void ensureRegistered() {
        if (registered) return;
        registered = true;
        Minecraft.getInstance().getTextureManager().register(ID, ClientCompat.texture("Retro64 Plus", build(false)));
        Minecraft.getInstance().getTextureManager().register(LUIGI_ID, ClientCompat.texture("Retro64 Plus", build(true)));
    }

    private static NativeImage build(boolean luigi) {
        NativeImage image = new NativeImage(WIDTH, HEIGHT, true);
        ClientCompat.fillAbgr(image, 0, 0, WIDTH, HEIGHT, 0);
        // Backgrounds behind the tiles (upstream's TexGenerator; colours in NativeImage's ABGR).
        ClientCompat.fillAbgr(image, 0, 0, 64, 32, 0xFF000000); // metal reflection
        ClientCompat.fillAbgr(image, 64, 0, 32, 32, luigi ? LUIGI_NAVY_ABGR : 0xFFFF0000); // overall buttons: blue
        ClientCompat.fillAbgr(image, 96, 0, 94, 32, luigi ? LUIGI_GREEN_ABGR : 0xFF0000FF); // cap logo: red
        ClientCompat.fillAbgr(image, 190, 0, 386, 64, 0xFF79C1FE); // sideburns, mustache, eyes: skin
        byte[] rgba = LibSM64.texture();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < Libsm64.TEXTURE_WIDTH; x++) {
                if (luigi && x >= LOGO_X && x < LOGO_X + LOGO_SIZE && y < LOGO_SIZE) continue; // drawn below
                int i = 4 * (x + Libsm64.TEXTURE_WIDTH * y);
                int abgr = (rgba[i + 3] & 0xFF) << 24 | (rgba[i + 2] & 0xFF) << 16 | (rgba[i + 1] & 0xFF) << 8
                        | (rgba[i] & 0xFF);
                ClientCompat.blendAbgr(image, x, y, abgr);
            }
        }
        if (luigi) drawLuigiLogo(image, rgba);
        ClientCompat.fillAbgr(image, Libsm64.TEXTURE_WIDTH, 0, 8, HEIGHT, 0xFFFFFFFF);
        return image;
    }

    /**
     * The ROM's logo is a white ellipse with a red "M". Luigi's: the same ellipse filled white, with a green "L" where
     * the "M" was (original artwork, so nothing but the ellipse's shape comes from the ROM).
     */
    private static void drawLuigiLogo(NativeImage image, byte[] rgba) {
        int mMinX = LOGO_SIZE, mMaxX = -1, mMinY = LOGO_SIZE, mMaxY = -1;
        for (int y = 0; y < LOGO_SIZE; y++) {
            int first = -1, last = -1;
            for (int x = 0; x < LOGO_SIZE; x++) {
                if (isWhite(rgba, LOGO_X + x, y)) {
                    if (first < 0) first = x;
                    last = x;
                }
            }
            if (first < 0) continue;
            for (int x = first; x <= last; x++) {
                ClientCompat.setPixelAbgr(image, LOGO_X + x, y, 0xFFFFFFFF);
                if (!isWhite(rgba, LOGO_X + x, y) && alpha(rgba, LOGO_X + x, y) > 128) { // the "M"
                    mMinX = Math.min(mMinX, x);
                    mMaxX = Math.max(mMaxX, x);
                    mMinY = Math.min(mMinY, y);
                    mMaxY = Math.max(mMaxY, y);
                }
            }
        }
        if (mMaxX < 0) return;
        int w = mMaxX - mMinX + 1, h = mMaxY - mMinY + 1;
        int lw = Math.max(3, Math.round(w * 0.6f)), left = mMinX + (w - lw) / 2;
        int stem = Math.max(2, Math.round(lw * 0.4f)), foot = Math.max(2, Math.round(h * 0.3f));
        ClientCompat.fillAbgr(image, LOGO_X + left, mMinY, stem, h, LUIGI_GREEN_ABGR);
        ClientCompat.fillAbgr(image, LOGO_X + left, mMaxY - foot + 1, lw, foot, LUIGI_GREEN_ABGR);
    }

    private static boolean isWhite(byte[] rgba, int x, int y) {
        int i = 4 * (x + Libsm64.TEXTURE_WIDTH * y);
        return (rgba[i + 3] & 0xFF) > 128 && (rgba[i] & 0xFF) > 200 && (rgba[i + 1] & 0xFF) > 200
                && (rgba[i + 2] & 0xFF) > 200;
    }

    private static int alpha(byte[] rgba, int x, int y) {
        return rgba[4 * (x + Libsm64.TEXTURE_WIDTH * y) + 3] & 0xFF;
    }

    /**
     * Luigi's colour for an untextured triangle (D33): Mario's red (cap, shirt) becomes green and his blue (overalls)
     * navy, at the same brightness; everything else (skin, gloves, shoes, hair) stays. Writes into {@code out}.
     */
    static void luigiColor(float r, float g, float b, float[] out) {
        if (r > 0.25f && g < 0.1f && b < 0.1f) {
            set(out, LUIGI_GREEN, r);
        } else if (b > 0.25f && r < 0.1f && g < 0.1f) {
            set(out, LUIGI_NAVY, b);
        } else {
            out[0] = r;
            out[1] = g;
            out[2] = b;
        }
    }

    private static void set(float[] out, float[] color, float brightness) {
        for (int i = 0; i < 3; i++) out[i] = color[i] * brightness;
    }
}
