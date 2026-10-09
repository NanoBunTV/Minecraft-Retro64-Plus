package com.nanobuntv.retro64plus.sm64;

import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.sm64.lib.Libsm64;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;

/**
 * The native library's lifecycle (client only; a dedicated server never loads it, D13). {@link #start} extracts the
 * library, loads it and runs {@code sm64_global_init} on a worker thread, so the game never freezes on it; from
 * {@link Status#READY} on, every call happens on the render thread through {@link #lib()}. libsm64 keeps global state,
 * so it's initialized once per game session.
 */
public final class LibSM64 {
    public enum Status {
        NOT_STARTED,
        LOADING,
        READY,
        /** Not Windows or Linux x64 (D5). */
        UNSUPPORTED_SYSTEM,
        /** Extracting or loading the library, or its init, failed; {@link #error()} says why. */
        FAILED
    }

    private static volatile Status status = initialStatus();
    private static volatile @Nullable String error;
    private static volatile @Nullable CompletableFuture<Void> loading;
    private static Libsm64 lib;
    /** Mario's texture atlas, extracted from the ROM: RGBA, {@link Libsm64#TEXTURE_WIDTH} × {@link Libsm64#TEXTURE_HEIGHT}. */
    private static byte[] texture;
    /** SM64's power meter from the ROM (D45); null if it couldn't be read (the hearts stay then). */
    private static RomHud.@Nullable PowerMeter powerMeter;
    /** The coin and the HUD's digits from the ROM (M3); null if they couldn't be read. */
    private static RomHud.@Nullable CoinArt coinArt;
    /** Object models from the ROM (M4); the ones that couldn't be read are missing. */
    private static Map<RomModels.Kind, RomModels.Model> models = Map.of();
    private static volatile boolean assetsRead;

    /** Native log lines (some come from the library's audio thread). Kept in a field: JNA must not lose it to GC. */
    private static final Libsm64.DebugPrint DEBUG_PRINT = message -> Retro64Plus.LOGGER.debug("[libsm64] {}", message);

    private LibSM64() {}

    private static Status initialStatus() {
        return NativePlatform.current().isPresent() ? Status.NOT_STARTED : Status.UNSUPPORTED_SYSTEM;
    }

    public static Status status() {
        return status;
    }

    public static @Nullable String error() {
        return error;
    }

    /**
     * Starts loading with this ROM, once; later calls return the same future. Completes on the worker thread
     * (exceptionally on failure, with {@link #status()} then {@link Status#FAILED}).
     */
    public static synchronized CompletableFuture<Void> start(Rom.Loaded rom, Path modDir) {
        if (loading != null) return loading;
        Optional<NativePlatform> platform = NativePlatform.current();
        if (platform.isEmpty()) {
            return loading = CompletableFuture.failedFuture(new IllegalStateException("unsupported system"));
        }
        status = Status.LOADING;
        loading = CompletableFuture.runAsync(() -> load(rom, modDir, platform.get()), runnable -> {
            Thread thread = new Thread(runnable, "Retro64 Plus native init");
            thread.setDaemon(true);
            thread.start();
        });
        return loading;
    }

    private static void load(Rom.Loaded rom, Path modDir, NativePlatform platform) {
        // The ROM's pictures and models are read in Java first, so they work even if the native library fails.
        readAssets(rom);
        try {
            Path file = Natives.extract(modDir, platform);
            Libsm64 loaded = Native.load(file.toAbsolutePath().toString(), Libsm64.class);
            int version = loaded.sm64_get_version();
            if (version != Libsm64.SUPPORTED_VERSION) {
                throw new IllegalStateException("libsm64 reports version " + version + ", expected "
                        + Libsm64.SUPPORTED_VERSION);
            }
            Memory romMemory = new Memory(rom.data().length);
            romMemory.write(0, rom.data(), 0, rom.data().length);
            Memory textureMemory = new Memory(4L * Libsm64.TEXTURE_WIDTH * Libsm64.TEXTURE_HEIGHT);
            long start = System.nanoTime();
            loaded.sm64_global_init(romMemory, textureMemory, DEBUG_PRINT); // copies the ROM; ours may be freed
            texture = textureMemory.getByteArray(0, (int) textureMemory.size());
            lib = loaded;
            Retro64Plus.LOGGER.info("libsm64 v{} ready ({} ms, ROM {})", version,
                    (System.nanoTime() - start) / 1_000_000, rom.path());
            status = Status.READY; // volatile write: publishes lib and texture to the render thread
        } catch (Throwable t) {
            // Never crash Minecraft (GameDesign pillar 4): Mario mode just stays off, with the reason shown.
            Retro64Plus.LOGGER.error("Couldn't load libsm64", t);
            error = t.getClass().getSimpleName() + ": " + t.getMessage();
            status = Status.FAILED;
            throw t instanceof RuntimeException r ? r : new RuntimeException(t);
        }
    }

    private static void readAssets(Rom.Loaded rom) {
        try {
            powerMeter = RomHud.powerMeter(rom.data());
        } catch (RuntimeException e) {
            Retro64Plus.LOGGER.warn("Couldn't read the power meter from the ROM; the hearts stay", e);
        }
        try {
            coinArt = RomHud.coinArt(rom.data());
        } catch (RuntimeException e) {
            Retro64Plus.LOGGER.warn("Couldn't read the coin from the ROM; coins use the mod's own look", e);
        }
        try {
            List<Exception> errors = new ArrayList<>();
            models = RomModels.load(rom.data(), errors);
            for (Exception e : errors) {
                Retro64Plus.LOGGER.warn("Couldn't read a model from the ROM; it uses the mod's own look", e);
            }
        } catch (RuntimeException e) {
            Retro64Plus.LOGGER.warn("Couldn't read the models from the ROM; they use the mod's own look", e);
        }
        assetsRead = true; // volatile write: publishes them to the render thread
    }

    /** Whether the ROM's pictures and models have been read (each may still be null if it failed). */
    public static boolean assetsRead() {
        return assetsRead;
    }

    /** The loaded library; only once {@link #status()} is {@link Status#READY}, and only on the render thread. */
    public static Libsm64 lib() {
        if (status != Status.READY) throw new IllegalStateException("libsm64 is " + status);
        return lib;
    }

    /** SM64's power meter textures from the ROM, or null; only once ready. */
    public static RomHud.@Nullable PowerMeter powerMeter() {
        if (status != Status.READY) throw new IllegalStateException("libsm64 is " + status);
        return powerMeter;
    }

    /** The coin and the HUD glyphs from the ROM, or null (not read yet, or failed). */
    public static RomHud.@Nullable CoinArt coinArt() {
        return assetsRead ? coinArt : null;
    }

    /** An object model from the ROM (M4), or null (not read yet, or failed). */
    public static RomModels.@Nullable Model model(RomModels.Kind kind) {
        return assetsRead ? models.get(kind) : null;
    }

    /** Mario's texture atlas from the ROM (RGBA); only once ready. */
    public static byte[] texture() {
        if (status != Status.READY) throw new IllegalStateException("libsm64 is " + status);
        return texture;
    }
}
