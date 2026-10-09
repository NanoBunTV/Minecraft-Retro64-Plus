package com.nanobuntv.retro64plus.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.config.PowerMeterPosition;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import com.nanobuntv.retro64plus.health.MarioHealth;
import com.nanobuntv.retro64plus.sm64.Actions;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import com.nanobuntv.retro64plus.sm64.RomHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

/**
 * SM64's power meter (D30, D45) in place of the hearts while you're Mario, drawn from the ROM's own textures, at the
 * top middle of the screen or left of the hotbar (config). As in SM64 it slides in when Mario is hurt, stays while he
 * isn't at full power or is under water, and slides away 1.5 s after he's back to full; or it's always shown (config).
 * Render thread.
 */
public final class PowerMeterHud {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "power_meter");
    /** The atlas: left half at (0, 0), right half at (32, 0), the middles for n segments at 64 + 32 (n-1 % 2), 32 (n-1 / 2). */
    private static final int ATLAS = 128;
    /** Drawn size (GUI pixels) of the 64-texel meter, and its gap to the screen's edge or the hotbar. */
    private static final float SIZE = 40, MARGIN = 3;
    /** Room vanilla's offhand slot takes beside the hotbar (Gui.renderItemHotbar: 29 px). */
    private static final float OFFHAND_SLOT = 29;
    /** How far it slides a tick (0 hidden, 1 shown): about a quarter second, as SM64's 20 units a frame. */
    private static final float SLIDE_PER_TICK = 0.22f;
    /** Ticks at full power before it hides: SM64's 45 frames. */
    private static final int FULL_TICKS = 30;

    private static boolean registered, failed;
    /** 0 hidden … 1 shown. */
    private static float shown, previousShown;
    private static int fullTicks;
    private static int lastSegments = -1;

    private PowerMeterHud() {}

    /** Vanilla's absorption heart, drawn beside the meter with a count (the meter shows health only). */
    private static final ResourceLocation ABSORBING_HEART = ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full");

    /** Whether the hearts are hidden: while you're Mario and can be hurt (not creative), with the meter read. */
    public static boolean replacesHearts() {
        Minecraft mc = Minecraft.getInstance();
        return LocalMario.isActive() && mc.gameMode != null && mc.gameMode.canHurtPlayer()
                && LibSM64.status() == LibSM64.Status.READY && LibSM64.powerMeter() != null;
    }

    /** Every client tick (from {@link Retro64PlusClient}): where the meter slides. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        previousShown = shown;
        if (!replacesHearts() || player == null) {
            shown = previousShown = 0;
            lastSegments = -1;
            return;
        }
        int segments = MarioHealth.segments(player.getHealth(), player.getMaxHealth());
        if (segments < MarioHealth.SEGMENTS || segments != lastSegments) fullTicks = 0;
        else fullTicks++;
        lastSegments = segments;
        LocalMario local = LocalMario.active();
        boolean underWater = local != null && local.mario().latest() != null
                && (local.mario().latest().action & Actions.GROUP_MASK) == Actions.GROUP_SUBMERGED;
        boolean show = Retro64PlusConfig.client().powerMeterAlwaysShown || segments < MarioHealth.SEGMENTS
                || underWater || fullTicks < FULL_TICKS;
        shown = Math.clamp(shown + (show ? SLIDE_PER_TICK : -SLIDE_PER_TICK), 0, 1);
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (ClientCompat.hudHidden() || mc.player == null || !replacesHearts()) return;
        float t = previousShown + (shown - previousShown) * partialTick;
        if (t <= 0) return;
        if (!registered && !register()) return;
        int segments = MarioHealth.segments(mc.player.getHealth(), mc.player.getMaxHealth());
        float eased = 1 - (1 - t) * (1 - t);
        float scale = SIZE / 64, half = SIZE / 2;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        float cx, cy;
        if (Retro64PlusConfig.client().powerMeterPosition == PowerMeterPosition.HOTBAR) {
            // Left of the hotbar (182 wide, centred), bottom-aligned with it; hidden below the screen.
            cx = w / 2f - 91 - MARGIN - half;
            // The offhand slot sits left of the hotbar (right-handed players) while it holds something: go past it.
            if (!mc.player.getOffhandItem().isEmpty() && mc.player.getMainArm() == HumanoidArm.RIGHT) {
                cx -= OFFHAND_SLOT;
            }
            cy = h - half - 1 + (1 - eased) * (SIZE + MARGIN);
        } else {
            // Top middle; hidden above the screen.
            cx = w / 2f;
            cy = MARGIN + half - (1 - eased) * (SIZE + MARGIN);
        }
        GuiDraw.blend(true);
        GuiDraw.push(graphics);
        GuiDraw.translate(graphics, cx, cy);
        GuiDraw.scale(graphics, scale, scale);
        GuiDraw.blit(graphics, TEXTURE, -32, -32, 0, 0, 32, 64, ATLAS, ATLAS);
        GuiDraw.blit(graphics, TEXTURE, 0, -32, 32, 0, 32, 64, ATLAS, ATLAS);
        if (segments > 0) {
            int i = segments - 1;
            GuiDraw.blit(graphics, TEXTURE, -16, -16, 64 + 32 * (i % 2), 32 * (i / 2), 32, 32, ATLAS, ATLAS);
        }
        GuiDraw.pop(graphics);
        // Absorption (golden apples, the Absorption effect): a golden heart and how many, right of the meter.
        int absorbing = (int) Math.ceil(mc.player.getAbsorptionAmount() / 2);
        if (absorbing > 0) {
            int x = Math.round(cx + half + 2), y = Math.round(cy - 4.5f);
            GuiDraw.sprite(graphics, ABSORBING_HEART, x, y, 9, 9);
            GuiDraw.text(graphics, mc.font, "×" + absorbing, x + 10, y + 1, 0xFFFFD34A);
        }
        GuiDraw.blend(false);
    }

    /** For dev diagnostics. */
    static String debugState() {
        return String.format(java.util.Locale.ROOT, "meter %.2f (%s), atlas %s", shown,
                shown <= 0 ? "hidden" : shown >= 1 ? "shown" : "sliding", registered ? "built" : failed ? "failed" : "not yet");
    }

    /** Builds the atlas from the ROM's textures, once. */
    private static boolean register() {
        if (failed) return false;
        RomHud.PowerMeter meter = LibSM64.powerMeter();
        if (meter == null) {
            failed = true;
            return false;
        }
        NativeImage image = new NativeImage(ATLAS, ATLAS, true);
        copy(meter.left(), image, 0, 0);
        copy(meter.right(), image, 32, 0);
        for (int i = 0; i < meter.segments().length; i++) copy(meter.segments()[i], image, 64 + 32 * (i % 2), 32 * (i / 2));
        Minecraft.getInstance().getTextureManager().register(TEXTURE, ClientCompat.texture("Retro64 Plus", image));
        registered = true;
        return true;
    }

    private static void copy(RomHud.Image from, NativeImage to, int x, int y) {
        for (int py = 0; py < from.height(); py++) {
            for (int px = 0; px < from.width(); px++) {
                int argb = from.argb(px, py);
                // NativeImage stores ABGR.
                int abgr = (argb & 0xFF00FF00) | (argb & 0xFF) << 16 | (argb >>> 16 & 0xFF);
                ClientCompat.setPixelAbgr(to, x + px, y + py, abgr);
            }
        }
    }
}
