package com.lukarbonite.autopickup.client;

import com.lukarbonite.autopickup.AutoPickupCommand;
import com.lukarbonite.autopickup.AutoPickupCommon;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import org.lwjgl.glfw.GLFW;

/**
 * NeoForge client-side event handler.
 * Mirrors the behaviour of {@code AutoPickupClient} (Fabric) using NeoForge events.
 */
@EventBusSubscriber(modid = AutoPickupCommon.MOD_ID, value = Dist.CLIENT)
public final class AutoPickupNeoForgeClient {

    private static boolean pendingSync = false;

    private static final KeyMapping.Category AUTOPICKUP_CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath(AutoPickupCommon.MOD_ID, "key.categories.autopickup")
            );

    private static final KeyMapping KEY_TOGGLE_AUTOPICKUP_MASTER = new KeyMapping(
            "key.autopickup.toggle_master",
            GLFW.GLFW_KEY_SEMICOLON,
            AUTOPICKUP_CATEGORY
    );

    static {
        AutoPickupKeyBindings.toggleMaster = KEY_TOGGLE_AUTOPICKUP_MASTER;
    }

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

        while (KEY_TOGGLE_AUTOPICKUP_MASTER.consumeClick()) {
            if (client.player != null) {
                if (ClientConfigManager.allowMaster) {
                    ClientConfigManager.getProfile().master = !ClientConfigManager.getProfile().master;
                    ClientConfigManager.save();
                    sendConfig(client);
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

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KEY_TOGGLE_AUTOPICKUP_MASTER);
    }
}
