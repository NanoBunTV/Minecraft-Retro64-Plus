package com.nanobuntv.retro64plus.sm64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NativePlatformTest {
    @Test
    void detectsSupportedSystems() {
        assertEquals(NativePlatform.WINDOWS_X86_64, NativePlatform.detect("Windows 10", "amd64").orElseThrow());
        assertEquals(NativePlatform.LINUX_X86_64, NativePlatform.detect("Linux", "x86_64").orElseThrow());
    }

    @Test
    void everythingElseIsUnsupported() {
        assertTrue(NativePlatform.detect("Mac OS X", "aarch64").isEmpty()); // D5: no macOS
        assertTrue(NativePlatform.detect("Linux", "aarch64").isEmpty()); // D5: no ARM
        assertTrue(NativePlatform.detect("Windows 11", "x86").isEmpty());
    }
}
