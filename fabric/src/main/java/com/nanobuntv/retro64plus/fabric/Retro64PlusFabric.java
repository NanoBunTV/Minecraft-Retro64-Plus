package com.nanobuntv.retro64plus.fabric;

import com.nanobuntv.retro64plus.Retro64Plus;
import net.fabricmc.api.ModInitializer;

public final class Retro64PlusFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Retro64Plus.init();
    }
}
