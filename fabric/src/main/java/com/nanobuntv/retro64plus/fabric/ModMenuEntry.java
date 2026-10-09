package com.nanobuntv.retro64plus.fabric;

import com.nanobuntv.retro64plus.client.Retro64PlusConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's Config button opens the YACL config screen (D23). Loaded only when Mod Menu is installed. */
public final class ModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return Retro64PlusConfigScreen::create;
    }
}
