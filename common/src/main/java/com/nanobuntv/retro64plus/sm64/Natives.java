package com.nanobuntv.retro64plus.sm64;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * Extracts the native library the mod jar carries ({@code natives/<os>-<arch>/}) to
 * {@code <gameDir>/retro64plus/natives/<os>-<arch>/}, rewriting it only when the jar's copy differs (a mod update).
 * Same approach as Cartridge's.
 */
public final class Natives {
    private Natives() {}

    public static Path extract(Path modDir, NativePlatform platform) throws IOException {
        String resource = "/natives/" + platform.id + "/" + platform.libraryFileName;
        Path target = modDir.resolve("natives").resolve(platform.id).resolve(platform.libraryFileName);
        if (Files.exists(target) && same(resource, target)) return target;
        Path dir = target.getParent();
        Files.createDirectories(dir);
        deleteOldCopies(target);
        // Write beside it and move, so a crash mid-write never leaves a half file.
        Path temp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
        try (InputStream in = open(resource)) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Windows won't overwrite a library another game instance still has loaded, but lets it be renamed.
            if (!Files.exists(target)) throw e;
            Files.move(target, target.resolveSibling(target.getFileName() + ".old-" + System.nanoTime()));
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        }
        return target;
    }

    private static InputStream open(String resource) throws IOException {
        InputStream in = Natives.class.getResourceAsStream(resource);
        if (in == null) throw new IOException("the mod jar has no " + resource);
        return in;
    }

    /** Whether the file on disk is the jar's, compared a block at a time. */
    private static boolean same(String resource, Path target) throws IOException {
        try (InputStream a = open(resource); InputStream b = Files.newInputStream(target)) {
            byte[] x = new byte[1 << 16], y = new byte[1 << 16];
            for (;;) {
                int n = a.readNBytes(x, 0, x.length), m = b.readNBytes(y, 0, y.length);
                if (n != m || !Arrays.equals(x, 0, n, y, 0, m)) return false;
                if (n < x.length) return true;
            }
        }
    }

    /** Removes copies set aside by an earlier update, once nothing has them loaded any more. */
    private static void deleteOldCopies(Path target) {
        String prefix = target.getFileName() + ".old-";
        try (var files = Files.list(target.getParent())) {
            files.filter(f -> f.getFileName().toString().startsWith(prefix)).forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException ignored) {
                    // still loaded; next time
                }
            });
        } catch (IOException ignored) {
            // nothing to clean
        }
    }
}
