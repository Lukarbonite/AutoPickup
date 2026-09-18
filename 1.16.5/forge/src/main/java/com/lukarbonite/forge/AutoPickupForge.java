package com.lukarbonite.forge;

import com.lukarbonite.autopickup.AutoPickupCommon;
import com.lukarbonite.autopickup.AutoPickupCommand;
import com.lukarbonite.autopickup.WorldConfigs;
import com.lukarbonite.autopickup.AutoPickupConfig;
import com.lukarbonite.autopickup.PlayerConfigs;
import com.lukarbonite.forge.client.AutoPickupForgeClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entry point: a thin wrapper only.
 * All logic lives in {@link AutoPickupCommon}; platform services are resolved via
 * ForgePlatformHelper (registered as a META-INF/services provider).
 *
 * <p>1.16.5 Forge predates the constructor-injected mod context and the ServerStarted/Stopping
 * events in {@code net.minecraftforge.event.server}, so this uses the FML-era equivalents.
 */
@Mod(AutoPickupCommon.MOD_ID)
public class AutoPickupForge {

    public AutoPickupForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        AutoPickupCommon.init();
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);

        // Client-only setup. Must be gated behind a dist check so the client class
        // (and its client-only signatures) is never referenced/loaded on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(AutoPickupForgeClient::onClientSetup);
            AutoPickupForgeClient.registerConfigScreen();
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        AutoPickupCommand.register(event.getDispatcher());
    }

    private void onServerStarted(FMLServerStartedEvent event) {
        WorldConfigs.load(event.getServer());
    }

    private void onServerStopping(FMLServerStoppingEvent event) {
        AutoPickupConfig.getInstance().save();
        PlayerConfigs.save();
    }
}
