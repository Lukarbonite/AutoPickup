package com.lukarbonite.fabric.client;

import com.lukarbonite.autopickup.AutoPickupCommand;
import com.lukarbonite.autopickup.client.AutoPickupKeyBindings;
import com.lukarbonite.autopickup.client.ClientConfigManager;
import com.lukarbonite.autopickup.client.ClientNetworkManager;
import com.lukarbonite.autopickup.client.ClientSyncHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class AutoPickupClient implements ClientModInitializer {

    private boolean pendingSync = false;

    private static final KeyMapping KEY_TOGGLE_AUTOPICKUP_MASTER = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                    "key.autopickup.toggle_master",
                    GLFW.GLFW_KEY_SEMICOLON,
                    "key.categories.autopickup"
            )
    );

    static {
        AutoPickupKeyBindings.toggleMaster = KEY_TOGGLE_AUTOPICKUP_MASTER;
    }

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

        // 2. Sync Packet Tunnel + Keybind handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingSync && client.player != null) {
                pendingSync = false;
                ClientNetworkManager.sendConfig();
                // Send permission check and global config request on join so the state is cached before the menu is opened
                client.player.connection.sendCommand("autopickup check_perm");
                client.player.connection.sendCommand("autopickup query_global");
            }

            while (KEY_TOGGLE_AUTOPICKUP_MASTER.consumeClick()) {
                if (client.player != null) {
                    if (ClientConfigManager.allowMaster) {
                        ClientConfigManager.getProfile().master = !ClientConfigManager.getProfile().master;
                        ClientConfigManager.save();
                        ClientNetworkManager.sendConfig();
                        client.player.sendSystemMessage(
                                Component.literal("AutoPickup Master Toggled: " + ClientConfigManager.isMaster())
                                        .withStyle(ChatFormatting.YELLOW)
                        );
                    } else {
                        client.player.sendSystemMessage(
                                Component.literal("The server does not allow changing the AutoPickup Master preference.")
                                        .withStyle(ChatFormatting.RED)
                        );
                    }
                }
            }
        });

        // 3. Chat Interception for Data Query, Permissions, and Global Config
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
