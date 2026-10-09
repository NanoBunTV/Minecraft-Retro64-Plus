package com.nanobuntv.retro64plus.fabric;

import com.nanobuntv.retro64plus.client.Retro64PlusClient;
import com.nanobuntv.retro64plus.fabric.platform.FabricNetworkHelper;
import net.fabricmc.api.ClientModInitializer;

public final class Retro64PlusFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricNetworkHelper.registerClientReceivers();
        Retro64PlusClient.init();
    }
}
