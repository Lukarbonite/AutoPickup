package com.lukarbonite.neoforge.client;

import com.lukarbonite.autopickup.AutoPickupCommon;
import com.lukarbonite.autopickup.client.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AutoPickupCommon.MOD_ID, value = Dist.CLIENT)
public final class AutoPickupNeoForgeClient {

    private static boolean pendingSync = false;

    static final KeyMapping KEY_TOGGLE_AUTOPICKUP_MASTER = new KeyMapping(
            "key.autopickup.toggle_master",
            GLFW.GLFW_KEY_SEMICOLON,
            "key.categories.autopickup"
    );

    static {
        if (ModList.get().isLoaded("yet_another_config_lib_v3")) {
            ClientSyncHandler.yaclScreenFactory = () -> AutoPickupYACLConfigScreen.create(null);
        }
        AutoPickupKeyBindings.toggleMaster = KEY_TOGGLE_AUTOPICKUP_MASTER;
    }

    /** Called from AutoPickupNeoForge on the MOD bus to register the key mapping. */
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KEY_TOGGLE_AUTOPICKUP_MASTER);
    }

    // Client-side connection events. PlayerEvent.PlayerLoggedIn/OutEvent are fired by the server,
    // so on a client they only ever came from the integrated server: the join-time sync never ran
    // when connecting to a dedicated server.
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientConfigManager.updateConnection();
        pendingSync = true;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientConfigManager.updateConnection();
        pendingSync = false;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (pendingSync && client.player != null) {
            pendingSync = false;
            ClientNetworkManager.sendConfig();
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
    }

    public static void registerConfigScreens(ModContainer modContainer) {
        if (ModList.get().isLoaded("yet_another_config_lib_v3")) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                    (container, parentScreen) -> AutoPickupYACLConfigScreen.create(parentScreen));
        } else {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                    (container, parentScreen) -> AutoPickupConfigScreen.create(parentScreen));
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
}
