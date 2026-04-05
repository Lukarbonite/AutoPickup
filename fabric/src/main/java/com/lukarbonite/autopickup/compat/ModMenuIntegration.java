package com.lukarbonite.autopickup.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Check if YACL is installed
        if (FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            return parent -> com.lukarbonite.autopickup.client.AutoPickupYACLConfigScreen.create(parent);
        }

        // Vanilla GUI
        return parent -> com.lukarbonite.autopickup.client.AutoPickupConfigScreen.create(parent);
    }
}