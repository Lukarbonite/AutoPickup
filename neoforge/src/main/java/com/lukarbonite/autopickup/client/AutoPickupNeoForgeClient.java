package com.lukarbonite.autopickup.client;

import com.lukarbonite.autopickup.AutoPickupCommand;
import com.lukarbonite.autopickup.AutoPickupCommon;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/**
 * NeoForge client-side event handler.
 * Mirrors the behaviour of {@code AutoPickupClient} (Fabric) using NeoForge events.
 */
@EventBusSubscriber(modid = AutoPickupCommon.MOD_ID, value = Dist.CLIENT)
public final class AutoPickupNeoForgeClient {

    private static boolean pendingSync = false;

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        pendingSync = true;
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // Reset state on disconnect
        pendingSync = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (pendingSync && client.player != null) {
            pendingSync = false;
            sendConfig(client);
            client.player.connection.sendCommand("autopickup check_perm");
            client.player.connection.sendCommand("autopickup query_global");
        }
    }

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        String text = event.getMessage().getString();
        if (text.startsWith("[AP_DATA] ") || text.startsWith("[AP_PERM] ") ||
                text.startsWith("[AP_GLOBAL] ") || text.startsWith("[AP_OPEN_GUI]")) {
            ClientSyncHandler.handleDataResponse(text);
            event.setCanceled(true);
        }
    }

    /** Builds the config bit-mask from {@link ClientConfigManager} and sends it to the server. */
    private static void sendConfig(Minecraft client) {
        if (client.player == null) return;
        int mask = 0;
        if (ClientConfigManager.isMaster())       mask |= AutoPickupCommand.FLAG_MASTER;
        if (ClientConfigManager.isBlocks())       mask |= AutoPickupCommand.FLAG_BLOCKS;
        if (ClientConfigManager.isBlockXp())      mask |= AutoPickupCommand.FLAG_BLOCK_XP;
        if (ClientConfigManager.isMobLoot())      mask |= AutoPickupCommand.FLAG_MOB_LOOT;
        if (ClientConfigManager.isMobXp())        mask |= AutoPickupCommand.FLAG_MOB_XP;
        if (ClientConfigManager.isSplitMobLoot()) mask |= AutoPickupCommand.FLAG_SPLIT_LOOT;
        if (ClientConfigManager.isSplitMobXp())   mask |= AutoPickupCommand.FLAG_SPLIT_XP;
        client.player.connection.sendCommand("ap_config_sync " + mask);
    }
}
