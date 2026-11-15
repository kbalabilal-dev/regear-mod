package com.regearmod;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Adds the "Configure" button for this mod in Mod Menu. Only loaded when Mod Menu is installed. */
public class RegearModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new RegearConfigScreen(parent);
    }
}
