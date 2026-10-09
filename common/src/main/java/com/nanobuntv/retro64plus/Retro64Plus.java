package com.nanobuntv.retro64plus;

import com.nanobuntv.retro64plus.caps.Retro64PlusItems;
import com.nanobuntv.retro64plus.coins.Coins;
import com.nanobuntv.retro64plus.server.ServerCoins;
import com.nanobuntv.retro64plus.server.ServerStars;
import com.nanobuntv.retro64plus.stars.Stars;
import com.nanobuntv.retro64plus.net.Retro64PlusNetwork;
import com.nanobuntv.retro64plus.platform.Services;
import com.nanobuntv.retro64plus.server.ServerMarios;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry point, called by each loader's mod initializer (both sides). */
public final class Retro64Plus {
    public static final String MOD_ID = "retro64plus";
    public static final Logger LOGGER = LoggerFactory.getLogger("Retro64 Plus");

    private Retro64Plus() {}

    public static void init() {
        LOGGER.info("Retro64 Plus starting on {}", Services.PLATFORM.loaderName());
        Retro64PlusItems.register();
        Coins.register();
        Stars.register();
        com.nanobuntv.retro64plus.enemies.Enemies.register();
        ServerMarios.init();
        ServerCoins.init();
        ServerStars.init();
        com.nanobuntv.retro64plus.server.ServerShop.init();
        Retro64PlusNetwork.register();
    }
}
