package com.lukarbonite.fabric.client;

import com.lukarbonite.autopickup.client.ClientConfigManager;
import com.lukarbonite.autopickup.client.ClientNetworkManager;
import com.lukarbonite.autopickup.client.ClientSyncHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

public class AutoPickupClient implements ClientModInitializer {

    private boolean pendingSync = false;

    @Override
    public void onInitializeClient() {
        if (FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
            ClientSyncHandler.yaclScreenFactory = () -> AutoPickupYACLConfigScreen.create(null);
        }

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientConfigManager.updateConnection();
            pendingSync = true;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientConfigManager.updateConnection();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingSync && client.player != null) {
                pendingSync = false;
                ClientNetworkManager.sendConfig();
                client.player.connection.sendCommand("autopickup check_perm");
                client.player.connection.sendCommand("autopickup query_global");
            }
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            String text = message.getString();
            if (text.startsWith("[AP_DATA] ") || text.startsWith("[AP_PERM] ") ||
                    text.startsWith("[AP_GLOBAL] ") || text.startsWith("[AP_OPEN_GUI]")) {
                ClientSyncHandler.handleDataResponse(text);
                return false;
            }
            return true;
        });
    }
}
