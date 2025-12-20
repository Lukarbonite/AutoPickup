package com.lukarbonite.autopickup.network;

import com.lukarbonite.autopickup.PlayerConfigs;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class AutoPickupNetworking {
    public static void init() {
        // Register the payload type (Common)
        PayloadTypeRegistry.playC2S().register(SyncConfigPayload.ID, SyncConfigPayload.CODEC);

        // Register the server-side receiver
        ServerPlayNetworking.registerGlobalReceiver(SyncConfigPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                PlayerConfigs.setPlayerConfig(
                        context.player().getUuid(),
                        payload.master(),
                        payload.blocks(),
                        payload.mobLoot(),
                        payload.xp()
                );
            });
        });

        // Clean up on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PlayerConfigs.removePlayer(handler.player.getUuid());
        });
    }
}