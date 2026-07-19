package com.lukarbonite.forge;

import com.lukarbonite.autopickup.AutoPickupCommon;
import com.lukarbonite.autopickup.AutoPickupConfig;
import com.lukarbonite.autopickup.PlayerConfigs;
import com.lukarbonite.forge.client.AutoPickupForgeClient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge entry point — thin wrapper only.
 * All logic lives in {@link AutoPickupCommon}; platform services are resolved via
 * ForgePlatformHelper (registered as a META-INF/services provider).
 */
@Mod(AutoPickupCommon.MOD_ID)
public class AutoPickupForge {

    public AutoPickupForge(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModContainer modContainer = context.getContainer();

        AutoPickupCommon.init();
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);

        // Client-only setup. Must be gated behind a dist check so the client class
        // (and its client-only signatures like net/minecraft/client/Options) is never
        // referenced/loaded on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Register key mappings on the MOD bus (client-only event)
            modEventBus.addListener(AutoPickupForgeClient::onRegisterKeyMappings);
            AutoPickupForgeClient.registerConfigScreens(modContainer);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        AutoPickupCommon.registerCommands(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        AutoPickupConfig.loadForWorld(event.getServer());
        PlayerConfigs.loadForWorld(event.getServer());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        AutoPickupConfig.getInstance().save();
        PlayerConfigs.save();
    }
}
