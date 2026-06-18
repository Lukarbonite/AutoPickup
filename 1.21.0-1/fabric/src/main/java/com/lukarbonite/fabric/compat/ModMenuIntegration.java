package com.lukarbonite.fabric.compat;

import com.lukarbonite.autopickup.client.AutoPickupConfigScreen;
import com.lukarbonite.fabric.client.AutoPickupYACLConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            return parent -> AutoPickupYACLConfigScreen.create(parent);
        }
        return AutoPickupConfigScreen::create;
    }
}
