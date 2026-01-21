package com.lukarbonite.autopickup;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoPickup implements ModInitializer {
    public static final String MOD_ID = "auto-pickup";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("AutoPickup");

    @Override
    public void onInitialize() {
        // Ensure config directory exists
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException e) {
            LOGGER.error("Failed to create AutoPickup config directory", e);
        }

        // Load Server Configuration
        AutoPickupConfig.getInstance().load();

        // Load Player Overrides (Admin settings)
        PlayerConfigs.load();

        // Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AutoPickupCommand.register(dispatcher);
        });

        LOGGER.info("Auto Pickup Mod initialized!");
    }
}