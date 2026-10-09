package com.nanobuntv.retro64plus.platform;

import com.nanobuntv.retro64plus.Retro64Plus;
import java.util.ServiceLoader;

/**
 * The platform layer (D8): common code calls loader-specific code only through these services. Each loader
 * project implements every interface and lists the implementation in META-INF/services.
 */
public final class Services {
    public static final PlatformHelper PLATFORM = load(PlatformHelper.class);
    public static final NetworkHelper NETWORK = load(NetworkHelper.class);

    private Services() {}

    public static <T> T load(Class<T> type) {
        T service = ServiceLoader.load(type, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation of " + type.getName()));
        Retro64Plus.LOGGER.debug("Loaded {} for {}", service, type.getName());
        return service;
    }
}
