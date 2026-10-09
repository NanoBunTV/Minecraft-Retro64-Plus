package com.nanobuntv.retro64plus.sm64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ROM detection and import (D22) without a real ROM: a fake ROM fails the hash, so these check byte orders, the
 * size and header filters, and that the player's file is never touched. The real-ROM path is checked in game.
 */
class RomTest {
    @TempDir
    Path dir;

    /** 8 MB with a .z64 header and random contents. */
    private static byte[] fakeZ64() {
        byte[] data = new byte[Rom.SIZE];
        new Random(64).nextBytes(data);
        data[0] = (byte) 0x80;
        data[1] = 0x37;
        data[2] = 0x12;
        data[3] = 0x40;
        return data;
    }

    @Test
    void v64AndN64AreConvertedToZ64() throws Exception {
        byte[] z64 = fakeZ64();
        byte[] v64 = z64.clone();
        for (int i = 0; i < v64.length; i += 2) {
            byte t = v64[i];
            v64[i] = v64[i + 1];
            v64[i + 1] = t;
        }
        byte[] n64 = z64.clone();
        for (int i = 0; i < n64.length; i += 4) {
            byte a = n64[i], b = n64[i + 1];
            n64[i] = n64[i + 3];
            n64[i + 1] = n64[i + 2];
            n64[i + 2] = b;
            n64[i + 3] = a;
        }
        Rom.toBigEndian(v64);
        Rom.toBigEndian(n64);
        assertArrayEquals(z64, v64);
        assertArrayEquals(z64, n64);
    }

    @Test
    void rejectsWrongSizeAndHeader() {
        Rom.InvalidRomException small = assertThrows(Rom.InvalidRomException.class, () -> Rom.toBigEndian(new byte[100]));
        assertEquals(Rom.Reason.NOT_N64_ROM, small.reason);
        Rom.InvalidRomException noHeader = assertThrows(Rom.InvalidRomException.class,
                () -> Rom.toBigEndian(new byte[Rom.SIZE]));
        assertEquals(Rom.Reason.NOT_N64_ROM, noHeader.reason);
    }

    @Test
    void anotherN64RomIsTheWrongRomAndIsNotImported() throws Exception {
        Path source = dir.resolve("other.z64");
        byte[] data = fakeZ64();
        Files.write(source, data);
        Rom.InvalidRomException e = assertThrows(Rom.InvalidRomException.class,
                () -> Rom.importRom(source, dir.resolve("retro64plus")));
        assertEquals(Rom.Reason.WRONG_ROM, e.reason);
        assertTrue(Files.notExists(dir.resolve("retro64plus").resolve(Rom.FILE_NAME)));
        assertArrayEquals(data, Files.readAllBytes(source)); // the player's file is untouched
    }

    @Test
    void findSkipsFilesThatDontMatch() throws Exception {
        Path folder = Files.createDirectories(dir.resolve("retro64plus"));
        Files.write(folder.resolve("fake.z64"), fakeZ64());
        Files.writeString(folder.resolve("notes.txt"), "hello");
        assertTrue(Rom.find(folder.resolve("missing.z64"), folder).isEmpty());
        assertTrue(Rom.find(null, dir.resolve("no such folder")).isEmpty());
    }

    @Test
    void sha1IsLowercaseHex() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", Rom.sha1(new byte[0]));
    }
}
