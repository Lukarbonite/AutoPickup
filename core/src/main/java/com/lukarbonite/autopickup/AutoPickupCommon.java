package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.platform.PlatformHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Platform-agnostic entry point.
 *
 * <p>Each platform module calls {@link #init()} from its entry class and registers
 * {@code AutoPickupCommand} from its command-registration event. The {@link PlatformHelper}
 * implementation is discovered automatically via {@link java.util.ServiceLoader}.
 *
 * <p>Lives in core/, which has no Minecraft dependency, so every version from 1.12.2 up shares it.
 */
public final class AutoPickupCommon {

    public static final String MOD_ID = "autopickup";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

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
}
