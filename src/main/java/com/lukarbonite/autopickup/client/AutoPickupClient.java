package com.lukarbonite.autopickup.client;

import com.lukarbonite.autopickup.AutoPickupCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class AutoPickupClient implements ClientModInitializer {

    private boolean pendingSync = false;

    @Override
    public void onInitializeClient() {
        // When we join a world/server, mark that we need to sync.
        // We do NOT call sendConfig() here to avoid the Race Condition with the Command Dispatcher.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            pendingSync = true;
        });

        // 2. Wait for the game to tick.
        // Once the player exists and the world is ticking, the Command System is guaranteed to be ready.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingSync && client.player != null) {
                pendingSync = false;
                AutoPickupCommand.sendConfig();
            }
        });
    }
}