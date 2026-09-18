package com.lukarbonite.fabric.client;

import com.lukarbonite.autopickup.client.AutoPickupKeyBindings;
import com.lukarbonite.autopickup.client.ClientConfigManager;
import com.lukarbonite.autopickup.client.ClientNetworkManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.TextComponent;
import org.lwjgl.glfw.GLFW;

/**
 * The server's hidden sync replies are intercepted by {@code ChatInterceptMixin} in the shared
 * 1.16.5 sources, since Fabric API for 1.16 has no chat receive event.
 */
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
                ClientNetworkManager.sendCommand("autopickup check_perm");
                ClientNetworkManager.sendCommand("autopickup query_global");
            }

            while (KEY_TOGGLE_AUTOPICKUP_MASTER.consumeClick()) {
                if (client.player != null) {
                    if (ClientConfigManager.allowMaster) {
                        ClientConfigManager.getProfile().master = !ClientConfigManager.getProfile().master;
                        ClientConfigManager.save();
                        ClientNetworkManager.sendConfig();
                        client.player.displayClientMessage(
                                new TextComponent("AutoPickup Master Toggled: " + ClientConfigManager.isMaster())
                                        .withStyle(ChatFormatting.YELLOW),
                                false
                        );
                    } else {
                        client.player.displayClientMessage(
                                new TextComponent("The server does not allow changing the AutoPickup Master preference.")
                                        .withStyle(ChatFormatting.RED),
                                false
                        );
                    }
                }
            }
        });
    }
}
