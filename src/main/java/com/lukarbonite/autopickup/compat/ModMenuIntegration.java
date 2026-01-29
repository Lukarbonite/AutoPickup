package com.lukarbonite.autopickup.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.NoticeScreen;
import net.minecraft.text.Text;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Check if YACL is installed
        if (FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            // Use a lambda to prevent the class AutoPickupConfigScreen from being loaded
            // until this specific line is executed.
            return parent -> com.lukarbonite.autopickup.client.AutoPickupConfigScreen.create(parent);
        }

        // Fallback: If YACL is missing, show a screen explaining why the menu won't open
        return parent -> new NoticeScreen(
                () -> net.minecraft.client.MinecraftClient.getInstance().setScreen(parent),
                Text.literal("Auto Pickup Config"),
                Text.literal("YetAnotherConfigLib (YACL) is required to use this menu. " +
                        "Please install it or use commands /autopickup instead.")
        );
    }
}