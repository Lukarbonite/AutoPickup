package com.lukarbonite.forge.client;

import com.lukarbonite.autopickup.client.AutoPickupKeyBindings;
import com.lukarbonite.autopickup.client.ClientConfigManager;
import com.lukarbonite.autopickup.client.ClientNetworkManager;
import com.lukarbonite.autopickup.client.ClientSyncHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.input.Keyboard;

/** Client-side handlers: join-time sync, the master toggle key, and hiding the server's sync replies. */
public final class AutoPickupForgeClient {

    // The FML connection events fire on the network thread, so they only raise flags for the client tick.
    private static volatile boolean pendingSync = false;
    private static volatile boolean pendingDisconnect = false;

    static final KeyBinding KEY_TOGGLE_AUTOPICKUP_MASTER = new KeyBinding(
            "key.autopickup.toggle_master",
            Keyboard.KEY_SEMICOLON,
            "key.categories.autopickup"
    );

    private AutoPickupForgeClient() {}

    /** Called from AutoPickupForge's pre-init on the client only. */
    public static void init() {
        AutoPickupKeyBindings.toggleMaster = KEY_TOGGLE_AUTOPICKUP_MASTER;
        ClientRegistry.registerKeyBinding(KEY_TOGGLE_AUTOPICKUP_MASTER);
        MinecraftForge.EVENT_BUS.register(new AutoPickupForgeClient());
    }

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        pendingDisconnect = false;
        pendingSync = true;
    }

    @SubscribeEvent
    public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        pendingSync = false;
        pendingDisconnect = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getMinecraft();

        if (pendingDisconnect) {
            pendingDisconnect = false;
            ClientConfigManager.useDefault(); // Reverts to default
        }

        if (pendingSync && client.player != null) {
            pendingSync = false;
            ClientConfigManager.updateConnection();
            ClientNetworkManager.sendConfig();
            ClientNetworkManager.sendCommand("autopickup check_perm");
            ClientNetworkManager.sendCommand("autopickup query_global");
        }

        while (KEY_TOGGLE_AUTOPICKUP_MASTER.isPressed()) {
            if (client.player == null) continue;
            if (ClientConfigManager.allowMaster) {
                ClientConfigManager.getProfile().master = !ClientConfigManager.getProfile().master;
                ClientConfigManager.save();
                ClientNetworkManager.sendConfig();
                client.player.sendStatusMessage(colored("AutoPickup Master Toggled: " + ClientConfigManager.isMaster(), TextFormatting.YELLOW), false);
            } else {
                client.player.sendStatusMessage(colored("The server does not allow changing the AutoPickup Master preference.", TextFormatting.RED), false);
            }
        }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String text = event.getMessage().getUnformattedText();
        if (ClientSyncHandler.isSyncMessage(text)) {
            ClientSyncHandler.handleDataResponse(text);
            event.setCanceled(true);
        }
    }

    private static TextComponentString colored(String text, TextFormatting color) {
        TextComponentString c = new TextComponentString(text);
        c.setStyle(new Style().setColor(color));
        return c;
    }
}
