package com.nanobuntv.retro64plus.client;

import com.nanobuntv.retro64plus.util.Compat;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.nanobuntv.retro64plus.MarioCharacter;
import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.coins.Coins;
import com.nanobuntv.retro64plus.config.Retro64PlusConfig;
import com.nanobuntv.retro64plus.platform.ClientServices;
import com.nanobuntv.retro64plus.platform.Services;
import com.nanobuntv.retro64plus.sm64.LibSM64;
import com.nanobuntv.retro64plus.sm64.NativePlatform;
import com.nanobuntv.retro64plus.sm64.Rom;
import com.nanobuntv.retro64plus.sm64.lib.Libsm64;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Client entry point, called by each loader's client initializer. Owns the Mario key (D14). The ROM is found and the
 * library loaded at startup, before the title screen ({@link RomSetupScreen}, D81); the key then turns Mario on and
 * off ({@link LocalMario}).
 */
public final class Retro64PlusClient {
    /** The Controls screen's group: a translation key up to 1.21.8, a registered category after. */
    //? if >=1.21.9 {
    /*public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Retro64Plus.MOD_ID, "main"));
    *///?} else {
    public static final String KEY_CATEGORY = "key.categories." + Retro64Plus.MOD_ID;
    //?}
    public static final KeyMapping TOGGLE_KEY = new KeyMapping("key." + Retro64Plus.MOD_ID + ".toggle",
            InputConstants.Type.KEYSYM, InputConstants.KEY_M, KEY_CATEGORY);
    /** SM64's B button: punch, kick, dive (D24). Jump and Sneak are Minecraft's own keys (A and Z). */
    public static final KeyMapping ACTION_KEY = new KeyMapping("key." + Retro64Plus.MOD_ID + ".action",
            InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, KEY_CATEGORY);
    /** Cycles the characters the server allows (D27); unbound by default. */
    public static final KeyMapping CHARACTER_KEY = new KeyMapping("key." + Retro64Plus.MOD_ID + ".character",
            InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);

    private static LibSM64.Status lastStatus = LibSM64.status();
    private static boolean searchingRom;
    /** Whether a forced-Mario server has already asked for the ROM this connection (asked once, not every tick). */
    private static boolean forcedPrompted;

    private Retro64PlusClient() {}

    public static void init() {
        Retro64PlusConfig.loadClient();
        ClientServices.EVENTS.registerKeyMapping(TOGGLE_KEY);
        ClientServices.EVENTS.registerKeyMapping(ACTION_KEY);
        ClientServices.EVENTS.registerKeyMapping(CHARACTER_KEY);
        ClientServices.EVENTS.onTickEnd(Retro64PlusClient::tick);
        ClientServices.EVENTS.registerEntityRenderer(Coins.COIN, CoinRenderer::new);
        ClientServices.EVENTS.registerEntityRenderer(com.nanobuntv.retro64plus.stars.Stars.POWER_STAR,
                PowerStarRenderer::new);
        ClientServices.EVENTS.registerEntityRenderer(com.nanobuntv.retro64plus.enemies.Enemies.GOOMBA,
                Sm64EnemyRenderer.Goomba::new);
        ClientServices.EVENTS.registerEntityRenderer(com.nanobuntv.retro64plus.enemies.Enemies.BOBOMB,
                Sm64EnemyRenderer.Bobomb::new);
        ClientServices.EVENTS.registerEntityRenderer(com.nanobuntv.retro64plus.enemies.Enemies.KOOPA,
                Sm64EnemyRenderer.Koopa::new);
        ClientServices.EVENTS.registerEntityRenderer(com.nanobuntv.retro64plus.enemies.Enemies.KOOPA_SHELL,
                KoopaShellRenderer::new);
        ClientServices.EVENTS.registerEntityRenderer(com.nanobuntv.retro64plus.enemies.Enemies.CHAIN_CHOMP,
                ChainChompRenderer::new);
        ClientServices.EVENTS.registerBlockEntityRenderer(com.nanobuntv.retro64plus.enemies.Enemies.CHOMP_POST_ENTITY,
                ChompPostRenderer::new);
        com.nanobuntv.retro64plus.enemies.EnemyEffects.install(MarioAudio::playFrom);
        DevAutoTest.init();
    }

    /** {@code <gamedir>/retro64plus/}: the ROM folder (D22) and the extracted natives. */
    public static Path modDir() {
        return Services.PLATFORM.gameDir().resolve(Retro64Plus.MOD_ID);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (TOGGLE_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) onTogglePressed();
        }
        while (CHARACTER_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) cycleCharacter();
        }
        LibSM64.Status status = LibSM64.status();
        if (status != lastStatus) {
            lastStatus = status;
            onStatusChanged(status);
        }
        if (mc.level == null) {
            MarioRules.reset();
            ClientCaps.reset();
            ClientCoins.reset();
            forcedPrompted = false;
        } else {
            enforceRules(mc);
        }
        if (mc.level != null && !mc.isPaused()) {
            com.nanobuntv.retro64plus.caps.CapCharge.clientTime = mc.level.getGameTime();
        }
        LocalMario.tick();
        RemoteMarios.tick();
        MarioAudio.tick();
        PowerMeterHud.tick();
        ControllerInput.poll();
        ControllerInput.tickActions(mc);
        DevAutoTest.tick();
    }

    /** The server's game rules (D14): a forced player becomes Mario as soon as they can, a forbidden one can't. */
    private static void enforceRules(Minecraft mc) {
        if (!MarioRules.allow() && LocalMario.isActive()) {
            LocalMario.turnOff();
            message(Component.translatable("message.retro64plus.not_allowed").withStyle(ChatFormatting.RED));
            return;
        }
        if (!MarioRules.force() || LocalMario.isActive() || mc.player == null || !mc.player.isAlive()
                || mc.player.isSpectator() || mc.screen != null) {
            return;
        }
        switch (LibSM64.status()) {
            case READY -> LocalMario.toggle();
            case NOT_STARTED -> {
                if (!forcedPrompted) {
                    forcedPrompted = true;
                    findRomAndStart(true);
                }
            }
            default -> { }
        }
    }

    private static void onTogglePressed() {
        switch (LibSM64.status()) {
            case UNSUPPORTED_SYSTEM -> message(Component.translatable("message.retro64plus.unsupported_system",
                    NativePlatform.describeCurrent()).withStyle(ChatFormatting.RED));
            case FAILED -> message(Component.translatable("message.retro64plus.load_failed", LibSM64.error())
                    .withStyle(ChatFormatting.RED));
            case LOADING -> actionBar(Component.translatable("message.retro64plus.loading"));
            case READY -> {
                if (LocalMario.isActive() && MarioRules.force()) {
                    actionBar(Component.translatable("message.retro64plus.forced"));
                } else if (!LocalMario.isActive() && !MarioRules.allow()) {
                    actionBar(Component.translatable("message.retro64plus.not_allowed"));
                } else {
                    LocalMario.toggle();
                }
            }
            case NOT_STARTED -> findRomAndStart(true);
        }
    }

    /** The character key (D27): the next character this server allows, saved as the choice. */
    static void cycleCharacter() {
        MarioCharacter next = MarioRules.character();
        for (int i = 0; i < MarioCharacter.values().length; i++) {
            next = next.next();
            if (MarioRules.allowed(next)) break;
        }
        Retro64PlusConfig.client().character = next.key();
        Retro64PlusConfig.saveClient();
        actionBar(Component.translatable("message.retro64plus.character",
                Component.translatable("retro64plus.character." + next.key())));
    }

    /**
     * Starts loading libsm64 if it hasn't been (normally the startup gate already did, D81), without asking for the
     * ROM.
     */
    static void loadQuietly() {
        findRomAndStart(false);
    }

    /**
     * Looks for a ROM off the render thread (it may read several 8 MB files), then loads it. The startup gate
     * normally loaded it already; this covers a gate passed by "Continue Without Mario" after a failure, which stays
     * failed, and dev runs.
     */
    private static void findRomAndStart(boolean tell) {
        if (searchingRom) return;
        searchingRom = true;
        if (tell) actionBar(Component.translatable("message.retro64plus.loading"));
        @Nullable Path configured = configuredRomPath();
        CompletableFuture.supplyAsync(() -> Rom.find(configured, modDir())).whenComplete((rom, error) ->
                Minecraft.getInstance().execute(() -> {
                    searchingRom = false;
                    if (rom != null && rom.isPresent()) {
                        start(rom.get());
                    } else if (tell) {
                        message(Component.translatable("message.retro64plus.no_rom").withStyle(ChatFormatting.RED));
                    }
                }));
    }

    /** Starts loading libsm64 with a checked ROM. */
    static void start(Rom.Loaded rom) {
        LibSM64.start(rom, modDir());
    }

    static @Nullable Path configuredRomPath() {
        String path = Retro64PlusConfig.client().romPath;
        if (path.isEmpty()) return null;
        try {
            return Path.of(path);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void onStatusChanged(LibSM64.Status status) {
        if (status == LibSM64.Status.READY) {
            message(Component.translatable("message.retro64plus.ready").withStyle(ChatFormatting.GREEN));
            if (Services.PLATFORM.isDevelopmentEnvironment()) dumpTexture();
        } else if (status == LibSM64.Status.FAILED) {
            message(Component.translatable("message.retro64plus.load_failed", LibSM64.error())
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** Dev builds: saves the texture atlas libsm64 extracted from the ROM, to check the extraction by eye. */
    private static void dumpTexture() {
        byte[] rgba = LibSM64.texture();
        Path file = modDir().resolve("debug").resolve("mario_atlas.png");
        try (NativeImage image = new NativeImage(Libsm64.TEXTURE_WIDTH, Libsm64.TEXTURE_HEIGHT, false)) {
            for (int y = 0; y < Libsm64.TEXTURE_HEIGHT; y++) {
                for (int x = 0; x < Libsm64.TEXTURE_WIDTH; x++) {
                    int i = 4 * (x + Libsm64.TEXTURE_WIDTH * y);
                    // NativeImage stores ABGR.
                    int abgr = (rgba[i + 3] & 0xFF) << 24 | (rgba[i + 2] & 0xFF) << 16 | (rgba[i + 1] & 0xFF) << 8
                            | (rgba[i] & 0xFF);
                    ClientCompat.setPixelAbgr(image, x, y, abgr);
                }
            }
            java.nio.file.Files.createDirectories(file.getParent());
            image.writeToFile(file);
            Retro64Plus.LOGGER.info("Wrote {}", file);
        } catch (Exception e) {
            Retro64Plus.LOGGER.warn("Couldn't write {}", file, e);
        }
    }

    static void message(Component text) {
        Optional.ofNullable(Minecraft.getInstance().player).ifPresent(p -> Compat.chat(p, text));
    }

    static void actionBar(Component text) {
        Optional.ofNullable(Minecraft.getInstance().player).ifPresent(p -> Compat.overlay(p, text));
    }
}
