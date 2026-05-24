package com.lukarbonite.forge.client;

import com.lukarbonite.autopickup.AutoPickupCommon;
import com.lukarbonite.autopickup.client.AutoPickupKeyBindings;
import com.lukarbonite.autopickup.client.ClientConfigManager;
import com.lukarbonite.autopickup.client.ClientNetworkManager;
import com.lukarbonite.autopickup.client.ClientSyncHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = AutoPickupCommon.MOD_ID, value = Dist.CLIENT)
public final class AutoPickupForgeClient {

    private static boolean pendingSync = false;

    static final KeyMapping KEY_TOGGLE_AUTOPICKUP_MASTER = new KeyMapping(
            "key.autopickup.toggle_master",
            GLFW.GLFW_KEY_SEMICOLON,
            "key.categories.autopickup"
    );

    static {
        AutoPickupKeyBindings.toggleMaster = KEY_TOGGLE_AUTOPICKUP_MASTER;
    }

    /** Called from AutoPickupForge on the MOD bus to register the key mapping. */
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KEY_TOGGLE_AUTOPICKUP_MASTER);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ClientConfigManager.updateConnection();
        pendingSync = true;
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ClientConfigManager.updateConnection();
        pendingSync = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
