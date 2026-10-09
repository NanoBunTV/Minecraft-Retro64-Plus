package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.config.ClientSettings;
import com.nanobuntv.retro64plus.config.CoinHudPosition;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The coin and star counters (D57), SM64-style: the coin, "×" and the count in SM64's HUD digits from the ROM, then the
 * star, "×" and the star count, at the top-left (or top-right, or hidden: config) while you're Mario, or always
 * (config). Without the ROM: the mod's icons and Minecraft's font. Render thread.
 */
public final class CoinHud {
    /** SM64 draws its 16-pixel HUD glyphs on a 320 × 240 screen; here at 12 GUI pixels. */
    private static final float GLYPH = 12, SCALE = GLYPH / 16;
    /** SM64's spacing: the icon, then "×" 16 px on, then digits 14 px on and 12 px apart. */
    private static final float TIMES_AT = 16 * SCALE, DIGITS_AT = 30 * SCALE, DIGIT_STEP = 12 * SCALE;
    /** Gap between the coin counter and the star counter. */
    private static final float GAP = 8;
    private static final int MARGIN = 4;
    /** Room vanilla's effect icons take at the top right. */
    private static final int EFFECTS_HEIGHT = 52;

    private CoinHud() {}

    static boolean shown() {
        Minecraft mc = Minecraft.getInstance();
        ClientSettings settings = Retro64PlusConfig.client();
        if (ClientCompat.hudHidden() || mc.player == null || mc.player.isSpectator()) return false;
        if (settings.coinCounterPosition == CoinHudPosition.HIDDEN) return false;
        return LocalMario.isActive() || settings.coinCounterAlwaysShown;
    }

    public static void render(GuiGraphics graphics) {
        if (!shown()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean rom = CoinArt.fromRom();
        String coins = Integer.toString(ClientCoins.coins()), stars = Integer.toString(ClientCoins.stars());
        float coinWidth = width(mc, rom, coins);
        float total = coinWidth + GAP + width(mc, rom, stars);
        float x = MARGIN, y = MARGIN;
        if (Retro64PlusConfig.client().coinCounterPosition == CoinHudPosition.TOP_RIGHT) {
            x = graphics.guiWidth() - MARGIN - total;
            if (!mc.player.getActiveEffects().isEmpty()) y += EFFECTS_HEIGHT;
        }
        ResourceLocation texture = CoinArt.texture();
        GuiDraw.blend(true);
        counter(graphics, mc, texture, rom, CoinArt.GLYPH_COIN, coins, x, y);
        counter(graphics, mc, texture, rom, CoinArt.GLYPH_STAR, stars, x + coinWidth + GAP, y);
        GuiDraw.blend(false);
    }

    private static float width(Minecraft mc, boolean rom, String count) {
        return rom ? DIGITS_AT + DIGIT_STEP * count.length() : DIGITS_AT - 2 + mc.font.width(count);
    }

    private static void counter(GuiGraphics graphics, Minecraft mc, ResourceLocation texture, boolean rom, int icon,
            String count, float x, float y) {
        glyph(graphics, texture, icon, x, y);
        if (rom) {
            glyph(graphics, texture, CoinArt.GLYPH_TIMES, x + TIMES_AT, y);
            for (int i = 0; i < count.length(); i++) {
                glyph(graphics, texture, count.charAt(i) - '0', x + DIGITS_AT + DIGIT_STEP * i, y);
            }
        } else {
            GuiDraw.text(graphics, mc.font, "×" + count, Math.round(x + TIMES_AT), Math.round(y + 2), 0xFFFFE04A);
        }
    }

    private static void glyph(GuiGraphics graphics, ResourceLocation texture, int index, float x, float y) {
        GuiDraw.push(graphics);
        GuiDraw.translate(graphics, x, y);
        GuiDraw.scale(graphics, SCALE, SCALE);
        GuiDraw.blit(graphics, texture, 0, 0, 16 * index, CoinArt.GLYPH_Y, 16, 16, CoinArt.WIDTH, CoinArt.HEIGHT);
        GuiDraw.pop(graphics);
    }
}
