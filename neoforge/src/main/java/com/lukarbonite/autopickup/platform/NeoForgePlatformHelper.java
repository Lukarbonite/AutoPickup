package com.lukarbonite.autopickup.platform;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * NeoForge implementation of {@link PlatformHelper}.
 * Registered as a service provider via
 * {@code META-INF/services/com.lukarbonite.autopickup.platform.PlatformHelper}.
 */
public final class NeoForgePlatformHelper implements PlatformHelper {

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
