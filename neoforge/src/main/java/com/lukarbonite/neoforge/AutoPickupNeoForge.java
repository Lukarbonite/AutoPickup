package com.lukarbonite.neoforge;

import com.lukarbonite.autopickup.AutoPickupCommon;
import com.lukarbonite.autopickup.client.AutoPickupConfigScreen;
import com.lukarbonite.neoforge.client.AutoPickupYACLConfigScreen;
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
 * NeoForgePlatformHelper (registered as a META-INF/services provider).
 *
 * NOTE: Class names here use Mojmap (unobfuscated). The common module was written with
 * yarn names, so common code included in this module will need name adjustments.
 */
@Mod(AutoPickupCommon.MOD_ID)
public class AutoPickupNeoForge {

    public AutoPickupNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        AutoPickupCommon.init();
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        if (ModList.get().isLoaded("yet_another_config_lib_v3")) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                    (container, parentScreen) -> AutoPickupYACLConfigScreen.create(parentScreen));
        } else {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                    (container, parentScreen) -> AutoPickupConfigScreen.create(parentScreen));
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        AutoPickupCommon.registerCommands(event.getDispatcher());
    }
}
