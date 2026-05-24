package com.lukarbonite.forge;

import com.lukarbonite.autopickup.AutoPickupCommon;
import com.lukarbonite.autopickup.AutoPickupConfig;
import com.lukarbonite.autopickup.PlayerConfigs;
import com.lukarbonite.autopickup.client.AutoPickupConfigScreen;
import com.lukarbonite.forge.client.AutoPickupForgeClient;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge entry point — thin wrapper only.
 * All logic lives in {@link AutoPickupCommon}; platform services are resolved via
 * ForgePlatformHelper (registered as a META-INF/services provider).
 */
@Mod(AutoPickupCommon.MOD_ID)
public class AutoPickupForge {

    public AutoPickupForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModContainer modContainer = ModList.get().getModContainerById(AutoPickupCommon.MOD_ID).orElseThrow();

        AutoPickupCommon.init();
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);

        // Register key mappings on the MOD bus (client-only event)
        modEventBus.addListener(AutoPickupForgeClient::onRegisterKeyMappings);

        modContainer.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parentScreen) ->
                        AutoPickupConfigScreen.create(parentScreen)));
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
