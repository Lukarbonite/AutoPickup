package com.lukarbonite.autopickup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * NeoForge entry point — thin wrapper only.
 * All logic lives in {@link AutoPickupCommon}; platform services are resolved via
 * {@code NeoForgePlatformHelper} (registered as a {@code META-INF/services} provider).
 */
@Mod(AutoPickupCommon.MOD_ID)
public class AutoPickupNeoForge {

    public AutoPickupNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        AutoPickupCommon.init();
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, parentScreen) -> {

            // Check if YACL is installed using NeoForge's ModList
            if (ModList.get().isLoaded("yet_another_config_lib_v3")) {
                // The lambda prevents the YACL-dependent class from loading until execution
                return com.lukarbonite.autopickup.client.AutoPickupConfigScreen.create(parentScreen);
            }

            // Fallback: If YACL is missing, show the alert screen
            return new AlertScreen(
                    () -> Minecraft.getInstance().setScreen(parentScreen),
                    Component.literal("Auto Pickup Config"),
                    Component.literal("YetAnotherConfigLib (YACL) is required to use this menu. " +
                            "Please install it or use commands /autopickup instead.")
            );
        });
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        AutoPickupCommon.registerCommands(event.getDispatcher());
    }
}
