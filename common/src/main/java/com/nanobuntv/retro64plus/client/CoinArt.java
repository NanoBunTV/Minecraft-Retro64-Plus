package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import com.nanobuntv.retro64plus.sm64.RomHud;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * The coin's texture (D50, D57): SM64's from the ROM once libsm64 is loaded, otherwise the mod's own stand-in
 * ({@code textures/entity/coin.png}, no Nintendo art). Both share one layout, 256 × 64: the four spin frames (32 × 32)
 * along the top, then a row of 16 × 16 HUD glyphs: digits 0–9, "×", the coin, the star. Render thread.
 */
public final class CoinArt {
    public static final int WIDTH = 256, HEIGHT = 64;
    public static final int GLYPH_Y = 32, GLYPH_TIMES = 10, GLYPH_COIN = 11, GLYPH_STAR = 12;
    private static final ResourceLocation ROM = ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "coin_rom");
    private static final ResourceLocation OWN =
            ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "textures/entity/coin.png");

    private static boolean built, failed;

    private CoinArt() {}

    /** The texture to draw with now. */
    public static ResourceLocation texture() {
        return fromRom() ? ROM : OWN;
    }

    /** Whether the ROM's art is in use (it has SM64's digits; the stand-in uses Minecraft's font for numbers). */
    public static boolean fromRom() {
        if (built) return true;
        if (failed || !LibSM64.assetsRead()) return false;
        RomHud.CoinArt art = LibSM64.coinArt();
        if (art == null) {
            failed = true;
            return false;
        }
        NativeImage image = new NativeImage(WIDTH, HEIGHT, true);
        for (int i = 0; i < art.spin().length; i++) copy(art.spin()[i], image, 32 * i, 0);
        for (int i = 0; i < art.digits().length; i++) copy(art.digits()[i], image, 16 * i, GLYPH_Y);
        copy(art.times(), image, 16 * GLYPH_TIMES, GLYPH_Y);
        copy(art.coin(), image, 16 * GLYPH_COIN, GLYPH_Y);
        copy(art.star(), image, 16 * GLYPH_STAR, GLYPH_Y);
        Minecraft.getInstance().getTextureManager().register(ROM, ClientCompat.texture("Retro64 Plus", image));
        built = true;
        return true;
    }

    private static void copy(RomHud.Image from, NativeImage to, int x, int y) {
        for (int py = 0; py < from.height(); py++) {
            for (int px = 0; px < from.width(); px++) {
                int argb = from.argb(px, py);
                // NativeImage stores ABGR.
                ClientCompat.setPixelAbgr(to, x + px, y + py, (argb & 0xFF00FF00) | (argb & 0xFF) << 16 | (argb >>> 16 & 0xFF));
            }
        }
    }
}
