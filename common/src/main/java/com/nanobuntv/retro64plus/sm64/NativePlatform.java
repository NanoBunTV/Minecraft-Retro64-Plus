package com.nanobuntv.retro64plus.sm64;

import java.util.Locale;
import java.util.Optional;

/** The platforms the native library is built for (D5): Windows x64 and Linux x64. */
public enum NativePlatform {
    WINDOWS_X86_64("windows-x86_64", "sm64.dll"),
    LINUX_X86_64("linux-x86_64", "libsm64.so");

    /** Folder name, as in the jar's natives/ and the game dir's retro64plus/natives/. */
    public final String id;
    public final String libraryFileName;

    NativePlatform(String id, String libraryFileName) {
        this.id = id;
        this.libraryFileName = libraryFileName;
    }

    /** This machine's platform, or empty on an unsupported OS or CPU (D5): Mario mode is then unavailable. */
    public static Optional<NativePlatform> current() {
        return detect(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    static Optional<NativePlatform> detect(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = osArch.toLowerCase(Locale.ROOT);
        if (!arch.equals("amd64") && !arch.equals("x86_64")) return Optional.empty();
        if (os.startsWith("windows")) return Optional.of(WINDOWS_X86_64);
        if (os.startsWith("linux")) return Optional.of(LINUX_X86_64);
        return Optional.empty();
    }

    /** "Windows 11 (amd64)", for the unsupported-system message. */
    public static String describeCurrent() {
        return System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")";
    }
}
