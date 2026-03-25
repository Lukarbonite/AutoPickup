package com.lukarbonite.autopickup;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * NeoForge entry point — thin wrapper only.
 * All logic lives in {@link AutoPickupCommon}; platform services are resolved via
 * {@code NeoForgePlatformHelper} (registered as a {@code META-INF/services} provider).
 */
@Mod(AutoPickupCommon.MOD_ID)
public class AutoPickupNeoForge {

    public AutoPickupNeoForge(IEventBus modEventBus) {
        AutoPickupCommon.init();
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        AutoPickupCommon.registerCommands(event.getDispatcher());
    }
}
