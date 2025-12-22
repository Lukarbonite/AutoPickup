package com.lukarbonite.autopickup.network;

import com.lukarbonite.autopickup.PlayerConfigs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class AutoPickupNetworking {
    public static void init() {
        // Register the server-side receiver
        ServerPlayNetworking.registerGlobalReceiver(SyncConfigPayload.ID, (server, player, handler, buf, responseSender) -> {
            // Read data from the packet on the network thread
            SyncConfigPayload payload = new SyncConfigPayload(buf);

            server.execute(() -> PlayerConfigs.setPlayerConfig(
                    player.getUuid(),
                    payload.master(),
                    payload.blocks(),
                    payload.mobLoot(),
                    payload.xp()
            ));
        });

        // Clean up on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PlayerConfigs.removePlayer(handler.player.getUuid()));
    }
}