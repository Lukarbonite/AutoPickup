package com.lukarbonite.neoforge;

import com.lukarbonite.autopickup.AutoPickupCommon;
import com.lukarbonite.autopickup.AutoPickupConfig;
import com.lukarbonite.autopickup.PlayerConfigs;
import com.lukarbonite.neoforge.client.AutoPickupNeoForgeClient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * NeoForge entry point — thin wrapper only.
 * All logic lives in {@link AutoPickupCommon}; platform services are resolved via
 * NeoForgePlatformHelper (registered as a META-INF/services provider).
 */
@Mod(AutoPickupCommon.MOD_ID)
public class AutoPickupNeoForge {

    public AutoPickupNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        AutoPickupCommon.init();
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        // Register key mappings on the MOD bus (client-only event)
        modEventBus.addListener(AutoPickupNeoForgeClient::onRegisterKeyMappings);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            AutoPickupNeoForgeClient.registerConfigScreens(modContainer);
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
