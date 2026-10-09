package com.nanobuntv.retro64plus.neoforge;

import com.nanobuntv.retro64plus.Retro64Plus;
import com.nanobuntv.retro64plus.client.Retro64PlusClient;
import com.nanobuntv.retro64plus.client.Retro64PlusConfigScreen;
import com.nanobuntv.retro64plus.neoforge.platform.NeoForgeClientEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Retro64Plus.MOD_ID, dist = Dist.CLIENT)
public final class Retro64PlusNeoForgeClient {
    public Retro64PlusNeoForgeClient(IEventBus modBus, ModContainer container) {
        // The YACL config screen (D23), from the mod list's Config button.
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> Retro64PlusConfigScreen.create(parent));
        NeoForgeClientEvents.attach(modBus);
        Retro64PlusClient.init();
    }
}
