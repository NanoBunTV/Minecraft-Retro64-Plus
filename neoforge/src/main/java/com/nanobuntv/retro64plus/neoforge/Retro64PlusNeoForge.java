package com.nanobuntv.retro64plus.neoforge;

import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.neoforge.platform.NeoForgeNetworkHelper;
import com.nanobuntv.retro64plus.neoforge.platform.NeoForgePlatformHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Retro64Plus.MOD_ID)
public final class Retro64PlusNeoForge {
    public Retro64PlusNeoForge(IEventBus modBus, ModContainer container) {
        Retro64Plus.init();
        NeoForgeNetworkHelper.attach(modBus);
        NeoForgePlatformHelper.attach(modBus);
    }
}
