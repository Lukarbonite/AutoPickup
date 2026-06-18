package com.lukarbonite.autopickup.client;

import com.lukarbonite.autopickup.AutoPickupCommon;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class AutoPickupFabricClient implements ClientModInitializer {

    private boolean pendingSync = false;

    private static final KeyMapping.Category AUTOPICKUP_CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath(AutoPickupCommon.MOD_ID, "key.categories.autopickup")
            );

    private static final KeyMapping KEY_TOGGLE_AUTOPICKUP_MASTER = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.autopickup.toggle_master",
                    GLFW.GLFW_KEY_SEMICOLON,
                    AUTOPICKUP_CATEGORY
            )
    );

    static {
        AutoPickupKeyBindings.toggleMaster = KEY_TOGGLE_AUTOPICKUP_MASTER;
    }

    @Override
    public void onInitializeClient() {
        // Handle Connection Switching (Profiles)
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientConfigManager.updateConnection();
            pendingSync = true;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientConfigManager.updateConnection(); // Reverts to default
        });

        // Sync Packet Tunnel + Keybind Handling
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

        // Chat Interception for Data Query, Permissions, and Global Config
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