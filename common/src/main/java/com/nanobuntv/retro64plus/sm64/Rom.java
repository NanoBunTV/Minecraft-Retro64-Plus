package com.nanobuntv.retro64plus.sm64;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/**
 * The player's Super Mario 64 ROM (D22). Only the US version works (the native library reads assets at its fixed
 * offsets), checked by SHA-1. Dumps in byte-swapped (.v64) or little-endian (.n64) order are converted to the native
 * big-endian (.z64) order first. The player's own file is only ever read: an imported ROM is <em>copied</em> into the
 * mod's folder (upstream moved it).
 */
public final class Rom {
    /** SHA-1 of the US ROM in big-endian (.z64) order. */
    public static final String SHA1 = "9bef1128717f958171a4afac3ed78ee2bb4e86ce";
    public static final int SIZE = 8 * 1024 * 1024;
    /** What an imported ROM is called in the mod's folder. */
    public static final String FILE_NAME = "baserom.us.z64";

    private Rom() {}

    /** A ROM that passed the check: where it was found, and its bytes in .z64 order. */
    public record Loaded(Path path, byte[] data) {}

    public static final class InvalidRomException extends IOException {
        public final Reason reason;

        InvalidRomException(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }
    }

    public enum Reason {
        /** Not 8 MB, or no N64 header: not an N64 ROM at all. */
        NOT_N64_ROM,
        /** An N64 ROM, but not Super Mario 64 (USA). */
        WRONG_ROM
    }

    /**
     * The first valid ROM: the configured path (if set), then any file in {@code folder}. Files that can't be read
     * or don't match are skipped.
     */
    public static Optional<Loaded> find(@Nullable Path configured, Path folder) {
        if (configured != null) {
            Optional<Loaded> rom = tryLoad(configured);
            if (rom.isPresent()) return rom;
        }
        if (!Files.isDirectory(folder)) return Optional.empty();
        // The imported copy first, then anything else the player dropped in (D22).
        Optional<Loaded> imported = tryLoad(folder.resolve(FILE_NAME));
        if (imported.isPresent()) return imported;
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Rom::isCandidate).sorted().map(Rom::tryLoad).flatMap(Optional::stream).findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Reads and checks a ROM file in any byte order. */
    public static Loaded load(Path file) throws IOException {
        byte[] data = Files.readAllBytes(file);
        toBigEndian(data);
        if (!SHA1.equals(sha1(data))) throw new InvalidRomException(Reason.WRONG_ROM);
        return new Loaded(file, data);
    }

    /**
     * Checks {@code source} and copies it, in .z64 order, to {@code folder/baserom.us.z64}. The source is untouched.
     */
    public static Loaded importRom(Path source, Path folder) throws IOException {
        Loaded rom = load(source);
        Files.createDirectories(folder);
        Path target = folder.resolve(FILE_NAME);
        Path temp = Files.createTempFile(folder, FILE_NAME, ".tmp");
        try {
            Files.write(temp, rom.data());
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        return new Loaded(target, rom.data());
    }

    /**
     * Rewrites an N64 ROM in place into big-endian (.z64) order, from whichever order its header shows.
     *
     * @throws InvalidRomException if it isn't 8 MB with an N64 header
     */
    static void toBigEndian(byte[] data) throws InvalidRomException {
        if (data.length != SIZE) throw new InvalidRomException(Reason.NOT_N64_ROM);
        int magic = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        switch (magic) {
            case 0x80371240 -> { } // .z64, native order
            case 0x37804012 -> { // .v64: each 16-bit word byte-swapped
                for (int i = 0; i < data.length; i += 2) swap(data, i, i + 1);
            }
            case 0x40123780 -> { // .n64: each 32-bit word little-endian
                for (int i = 0; i < data.length; i += 4) {
                    swap(data, i, i + 3);
                    swap(data, i + 1, i + 2);
                }
            }
            default -> throw new InvalidRomException(Reason.NOT_N64_ROM);
        }
    }

    static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every JVM has SHA-1
        }
    }

    private static Optional<Loaded> tryLoad(Path file) {
        if (!isCandidate(file)) return Optional.empty();
        try {
            return Optional.of(load(file));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /** Cheap filter before reading 8 MB: a regular file of exactly the ROM's size. */
    private static boolean isCandidate(Path file) {
        try {
            return Files.isRegularFile(file) && Files.size(file) == SIZE;
        } catch (IOException e) {
            return false;
        }
    }

    private static void swap(byte[] data, int a, int b) {
        byte t = data[a];
        data[a] = data[b];
        data[b] = t;
    }
}
