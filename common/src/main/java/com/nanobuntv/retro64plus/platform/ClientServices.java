package com.nanobuntv.retro64plus.platform;

/** Client-only services, kept apart from {@link Services} so a dedicated server never looks them up. */
public final class ClientServices {
    public static final ClientEvents EVENTS = Services.load(ClientEvents.class);

    private ClientServices() {}
}
