package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.platform.PlatformHelper;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Platform-agnostic entry point.
 *
 * <p>Each platform module calls {@link #init()} from its entry class and
 * {@link #registerCommands(CommandDispatcher)} from its command-registration event.
 * The {@link PlatformHelper} implementation is discovered automatically via
 * {@link java.util.ServiceLoader}.
 */
public final class AutoPickupCommon {

    public static final String MOD_ID = "autopickup";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private AutoPickupCommon() {}

    /**
     * Returns the mod's config directory ({@code <configRoot>/AutoPickup/}).
     * Safe to call any time after the JVM has loaded the platform JAR.
     */
    public static Path getConfigDir() {
        return PlatformHelper.get().getConfigDir().resolve("AutoPickup");
    }

    /**
     * Server-side initialisation. Call this from the platform entry point.
     * The {@link PlatformHelper} is resolved via {@link java.util.ServiceLoader}.
     */
    public static void init() {
        try {
            Files.createDirectories(getConfigDir());
        } catch (IOException e) {
            LOGGER.error("Failed to create AutoPickup config directory", e);
        }

        AutoPickupConfig.getInstance().load();
        PlayerConfigs.load();

        LOGGER.info("Auto Pickup Mod initialized!");
    }

    /**
     * Registers all commands. Call this from the platform's command-registration event
     * (Fabric: {@code CommandRegistrationCallback}, Forge: {@code RegisterCommandsEvent}).
     */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        AutoPickupCommand.register(dispatcher);
    }
}