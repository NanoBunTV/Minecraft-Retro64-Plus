package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.joml.Quaternionf;
//? if >=1.21.5 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
*///?} else {
import com.mojang.blaze3d.platform.GlStateManager;
//?}

/** Small client-side differences between Minecraft versions, each in one place (Porting.md; util/Compat on both sides). */
public final class ClientCompat {
    private ClientCompat() {}

    /** A texture from an image, for the texture manager. */
    public static DynamicTexture texture(String label, NativeImage image) {
        //? if >=1.21.5 {
        /*return new DynamicTexture(() -> label, image);
        *///?} else {
        return new DynamicTexture(image);
        //?}
    }

    /**
     * A texture from an image that repeats (or not) on each axis, nearest filtered (SM64's look). Minecraft's own
     * textures are nearest filtered too; up to 1.21.4 the wrap is set on the bound GL texture.
     */
    public static DynamicTexture texture(String label, NativeImage image, boolean repeatU, boolean repeatV) {
        //? if >=1.21.5 {
        /*return new DynamicTexture(() -> label, image) {
            {
                sampler = RenderSystem.getSamplerCache().getSampler(repeatU ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE,
                        repeatV ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST,
                        false);
            }
        };
        *///?} else {
        DynamicTexture texture = new DynamicTexture(image);
        texture.bind();
        GlStateManager._texParameter(0x0DE1, 0x2802, repeatU ? 0x2901 : 0x812F); // GL_TEXTURE_WRAP_S: REPEAT, CLAMP
        GlStateManager._texParameter(0x0DE1, 0x2803, repeatV ? 0x2901 : 0x812F); // GL_TEXTURE_WRAP_T
        return texture;
        //?}
    }

    /** Sets a pixel from {@code 0xAABBGGRR} (the image's own byte order; 1.21.2 renamed the method). */
    public static void setPixelAbgr(NativeImage image, int x, int y, int abgr) {
        //? if >=1.21.2 {
        /*image.setPixelABGR(x, y, abgr);
        *///?} else {
        image.setPixelRGBA(x, y, abgr);
        //?}
    }

    /** A pixel as {@code 0xAABBGGRR} (from 1.21.2 the image's own getter speaks ARGB). */
    public static int getPixelAbgr(NativeImage image, int x, int y) {
        //? if >=1.21.2 {
        /*int argb = image.getPixel(x, y);
        return argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >>> 16 & 0xFF;
        *///?} else {
        return image.getPixelRGBA(x, y);
        //?}
    }

    /** Fills a rectangle with {@code 0xAABBGGRR} (from 1.21.2 the image's own fillRect speaks ARGB). */
    public static void fillAbgr(NativeImage image, int x, int y, int width, int height, int abgr) {
        for (int py = y; py < y + height; py++) {
            for (int px = x; px < x + width; px++) setPixelAbgr(image, px, py, abgr);
        }
    }

    /** Blends {@code 0xAABBGGRR} over a pixel, as 1.21.1's NativeImage.blendPixel did (gone from 1.21.2). */
    public static void blendAbgr(NativeImage image, int x, int y, int abgr) {
        int under = getPixelAbgr(image, x, y);
        float a = (abgr >>> 24) / 255f, ua = (under >>> 24) / 255f, keep = 1 - a;
        int outA = channel(a * a + ua * keep);
        int outB = channel((abgr >>> 16 & 0xFF) / 255f * a + (under >>> 16 & 0xFF) / 255f * keep);
        int outG = channel((abgr >>> 8 & 0xFF) / 255f * a + (under >>> 8 & 0xFF) / 255f * keep);
        int outR = channel((abgr & 0xFF) / 255f * a + (under & 0xFF) / 255f * keep);
        setPixelAbgr(image, x, y, outA << 24 | outB << 16 | outG << 8 | outR);
    }

    private static int channel(float value) {
        return (int) (Math.min(1, value) * 255);
    }

    /** Whether F1 hid the HUD (26.2 moved the flag into the HUD itself). */
    public static boolean hudHidden() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gui.hud.isHidden();
        *///?} else {
        return Minecraft.getInstance().options.hideGui;
        //?}
    }

    /** The camera's rotation (26.2 renamed getMainCamera to mainCamera). */
    public static Quaternionf cameraRotation() {
        //? if >=26.2 {
        /*return new Quaternionf(Minecraft.getInstance().gameRenderer.mainCamera().rotation());
        *///?} else {
        return new Quaternionf(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        //?}
    }
}
