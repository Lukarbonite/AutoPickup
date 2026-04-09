package com.lukarbonite.fabric;

import com.lukarbonite.autopickup.AutoPickupCommon;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Fabric entry point — thin wrapper only.
 * All logic lives in {@link AutoPickupCommon}; platform services are resolved via
 * {@code FabricPlatformHelper} (registered as a {@code META-INF/services} provider).
 */
public class AutoPickup implements ModInitializer {

    @Override
    public void onInitialize() {
        AutoPickupCommon.init();

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        AutoPickupCommon.registerCommands(dispatcher));
    }
}