package com.lukarbonite.forge.client;

import com.lukarbonite.autopickup.AutoPickupCommon;
import com.lukarbonite.autopickup.client.AutoPickupConfigScreen;
import com.lukarbonite.autopickup.client.AutoPickupKeyBindings;
import com.lukarbonite.autopickup.client.ClientConfigManager;
import com.lukarbonite.autopickup.client.ClientNetworkManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The server's hidden sync replies are intercepted by {@code ChatInterceptMixin} in the shared
 * 1.16.5 sources, so both loaders handle them the same way.
 */
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

    /**
     * Registered on the MOD bus from AutoPickupForge. Deliberately not annotated: the annotated
     * handlers in this class go to the Forge bus, where a setup event would never fire.
     */
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientRegistry.registerKeyBinding(KEY_TOGGLE_AUTOPICKUP_MASTER);
    }

    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,
                () -> (minecraft, parentScreen) -> AutoPickupConfigScreen.create(parentScreen));
    }

    // Client-side connection events, so the join-time sync also runs on dedicated servers,
    // not only in singleplayer where the integrated server's login events would fire.
    @SubscribeEvent
    public static void onLoggedIn(ClientPlayerNetworkEvent.LoggedInEvent event) {
        ClientConfigManager.updateConnection();
        pendingSync = true;
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggedOutEvent event) {
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
    }
}
