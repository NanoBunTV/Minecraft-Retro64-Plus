package com.nanobuntv.retro64plus.config;

import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.platform.Services;
import java.nio.file.Path;

/**
 * Settings (D23), the same JSON5 files on every loader ({@link SettingsFile}): the client's
 * ({@code config/retro64plus-client.json5}, its screen is client/Retro64PlusConfigScreen) and the server's
 * ({@code config/retro64plus-server.json5}, D34; file only, read when a server or singleplayer world starts). Values
 * are plain fields; after changing the client's, {@link #saveClient}.
 */
public final class Retro64PlusConfig {
    private static final SettingsFile<ClientSettings> CLIENT = new SettingsFile<>(
            configDir().resolve(Retro64Plus.MOD_ID + "-client.json5"), ClientSettings.class, ClientSettings::new);
    private static final SettingsFile<ServerSettings> SERVER = new SettingsFile<>(
            configDir().resolve(Retro64Plus.MOD_ID + "-server.json5"), ServerSettings.class, ServerSettings::new);
    private static boolean clientLoaded;

    private Retro64PlusConfig() {}

    /** Client init. Writes the file if missing, and fills in new keys and comments. */
    public static void loadClient() {
        CLIENT.load();
        CLIENT.instance().clean();
        CLIENT.save();
        clientLoaded = true;
    }

    public static ClientSettings client() {
        if (!clientLoaded) loadClient();
        return CLIENT.instance();
    }

    public static ClientSettings clientDefaults() {
        return CLIENT.defaults();
    }

    public static void saveClient() {
        CLIENT.instance().clean();
        CLIENT.save();
    }

    /** Server start. Writes the file if missing, and fills in new keys and comments. */
    public static void loadServer() {
        SERVER.load();
        SERVER.instance().clean();
        SERVER.save();
    }

    /** The server's settings as last loaded (defaults before a server started). */
    public static ServerSettings server() {
        return SERVER.instance();
    }

    private static Path configDir() {
        return Services.PLATFORM.gameDir().resolve("config");
    }
}
