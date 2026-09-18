package com.lukarbonite.autopickup.client;

import com.lukarbonite.autopickup.AutoPickupCommand;
import net.minecraft.client.Minecraft;

public final class ClientNetworkManager {

    private ClientNetworkManager() {}

    /**
     * Builds a bit-mask from the current client profile and sends it to the server
     * via the {@code ap_config_sync} command tunnel.
     */
    public static void sendConfig() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        int mask = 0;
        if (ClientConfigManager.isMaster()) mask |= AutoPickupCommand.FLAG_MASTER;
        if (ClientConfigManager.isBlocks()) mask |= AutoPickupCommand.FLAG_BLOCKS;
        if (ClientConfigManager.isBlockXp()) mask |= AutoPickupCommand.FLAG_BLOCK_XP;
        if (ClientConfigManager.isMobLoot()) mask |= AutoPickupCommand.FLAG_MOB_LOOT;
        if (ClientConfigManager.isMobXp()) mask |= AutoPickupCommand.FLAG_MOB_XP;
        if (ClientConfigManager.isSplitMobLoot()) mask |= AutoPickupCommand.FLAG_SPLIT_LOOT;
        if (ClientConfigManager.isSplitMobXp()) mask |= AutoPickupCommand.FLAG_SPLIT_XP;

        sendCommand("ap_config_sync " + mask);
    }

    /**
     * Runs a command as the local player. 1.16.5 has no serverbound command packet (that arrived in
     * 1.19; the 1.13 commands packet only sends the command tree to the client for completion), so
     * commands are sent as a chat message with a leading slash, exactly as typing them would.
     */
    public static void sendCommand(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.chat("/" + command);
        }
    }
}
