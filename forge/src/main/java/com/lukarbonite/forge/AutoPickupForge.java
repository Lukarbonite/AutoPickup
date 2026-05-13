package com.lukarbonite.forge;

import com.lukarbonite.autopickup.AutoPickupCommon;
import com.lukarbonite.autopickup.client.AutoPickupConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge entry point — thin wrapper only.
 * All logic lives in {@link AutoPickupCommon}; platform services are resolved via
 * ForgePlatformHelper (registered as a META-INF/services provider).
 *
 * NOTE: Uses Mojmap names. Common code uses yarn names and will need reconciliation.
 */
@Mod(AutoPickupCommon.MOD_ID)
public class AutoPickupForge {

    public AutoPickupForge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModContainer modContainer = ModList.get().getModContainerById(AutoPickupCommon.MOD_ID).orElseThrow();

        AutoPickupCommon.init();
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);

        modContainer.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parentScreen) ->
                        AutoPickupConfigScreen.create(parentScreen)));
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        AutoPickupCommon.registerCommands(event.getDispatcher());
    }
}
