package com.lukarbonite.fabric;

import com.lukarbonite.autopickup.AutoPickupCommon;
import com.lukarbonite.autopickup.AutoPickupConfig;
import com.lukarbonite.autopickup.PlayerConfigs;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Fabric entry point — thin wrapper only.
 * All logic lives in {@link AutoPickupCommon}; platform services are resolved via
 * FabricPlatformHelper (registered as a META-INF/services provider).
 */
public class AutoPickupFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        AutoPickupCommon.init();

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) ->
                        AutoPickupCommon.registerCommands(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            AutoPickupConfig.loadForWorld(server);
            PlayerConfigs.loadForWorld(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            AutoPickupConfig.getInstance().save();
            PlayerConfigs.save();
        });
    }
}
