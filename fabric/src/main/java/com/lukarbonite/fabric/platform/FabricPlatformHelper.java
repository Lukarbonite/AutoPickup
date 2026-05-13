package com.lukarbonite.fabric.platform;

import com.lukarbonite.autopickup.platform.PlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Fabric implementation of {@link PlatformHelper}.
 * Registered as a service provider via
 * META-INF/services/com.lukarbonite.autopickup.platform.PlatformHelper.
 */
public final class FabricPlatformHelper implements PlatformHelper {

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoadedEarly(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
