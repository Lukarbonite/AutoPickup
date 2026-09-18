package com.lukarbonite.autopickup;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Fabric entry point — thin wrapper only.
 * All logic lives in {@link AutoPickupCommon}; platform services are resolved via
 * {@code FabricPlatformHelper} (registered as a {@code META-INF/services} provider).
 */
public class AutoPickupFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        AutoPickupCommon.init();

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        AutoPickupCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            WorldConfigs.load(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            AutoPickupConfig.getInstance().save();
            PlayerConfigs.save();
        });
    }
}