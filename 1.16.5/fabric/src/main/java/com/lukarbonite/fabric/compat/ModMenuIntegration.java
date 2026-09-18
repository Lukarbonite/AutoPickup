package com.lukarbonite.fabric.compat;

import com.lukarbonite.autopickup.client.AutoPickupConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // YACL does not exist for 1.16.5, so the vanilla screen is the only option.
        return AutoPickupConfigScreen::create;
    }
}
