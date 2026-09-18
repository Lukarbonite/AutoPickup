package com.lukarbonite.fabric;

import com.lukarbonite.autopickup.AutoPickupCommon;
import com.lukarbonite.autopickup.AutoPickupCommand;
import com.lukarbonite.autopickup.WorldConfigs;
import com.lukarbonite.autopickup.AutoPickupConfig;
import com.lukarbonite.autopickup.PlayerConfigs;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Fabric entry point: a thin wrapper only.
 * All logic lives in {@link AutoPickupCommon}; platform services are resolved via
 * {@code FabricPlatformHelper} (registered as a {@code META-INF/services} provider).
 */
public class AutoPickup implements ModInitializer {

    @Override
    public void onInitialize() {
        AutoPickupCommon.init();

        // Fabric API for 1.16 only has the v1 callback, which passes a "dedicated" flag
        // instead of the registry access and environment that v2 provides.
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, dedicated) -> AutoPickupCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            WorldConfigs.load(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            AutoPickupConfig.getInstance().save();
            PlayerConfigs.save();
        });
    }
}
