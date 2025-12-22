package com.lukarbonite.autopickup.client;

import com.lukarbonite.autopickup.AutoPickupConfig;
import com.lukarbonite.autopickup.network.SyncConfigPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class AutoPickupClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Send config when joining a server
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            sendConfig();
        });
    }

    public static void sendConfig() {
        if (ClientPlayNetworking.canSend(SyncConfigPayload.ID)) {
            AutoPickupConfig config = AutoPickupConfig.getInstance();
            ClientPlayNetworking.send(new SyncConfigPayload(
                    config.autoPickup,
                    config.autoPickupBlocks,
                    config.autoPickupMobLoot,
                    config.autoPickupXp
            ));
        }
    }
}