package com.lukarbonite.autopickup.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class AutoPickupClient implements ClientModInitializer {

    private boolean pendingSync = false;

    @Override
    public void onInitializeClient() {
        // 1. Handle Connection Switching (Profiles)
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientConfigManager.updateConnection();
            pendingSync = true;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientConfigManager.updateConnection(); // Reverts to default
        });

        // 2. Sync Packet Tunnel
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingSync && client.player != null) {
                pendingSync = false;
                ClientNetworkManager.sendConfig();
                // Send permission check and global config request on join so the state is cached before the menu is opened
                client.player.connection.sendCommand("autopickup check_perm");
                client.player.connection.sendCommand("autopickup query_global");
            }
        });

        // 3. Chat Interception for Data Query, Permissions, and Global Config
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            String text = message.getString();
            if (text.startsWith("[AP_DATA] ") || text.startsWith("[AP_PERM] ") || text.startsWith("[AP_GLOBAL] ")) {
                ClientSyncHandler.handleDataResponse(text);
                return false;
            }
            return true;
        });
    }
}