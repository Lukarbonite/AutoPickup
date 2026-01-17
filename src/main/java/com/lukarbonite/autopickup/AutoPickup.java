package com.lukarbonite.autopickup;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoPickup implements ModInitializer {
    public static final String MOD_ID = "auto-pickup";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Load Configuration
        AutoPickupConfig.getInstance().load();

        // Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AutoPickupCommand.register(dispatcher);
        });

        LOGGER.info("Auto Pickup Mod initialized!");
    }
}