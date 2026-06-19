package com.lukarbonite.autopickup.platform;

import net.fabricmc.loader.api.FabricLoader;
import com.lukarbonite.autopickup.client.ClientConfigManager;

import java.nio.file.Path;

/**
 * Fabric implementation of {@link PlatformHelper}.
 * Registered as a service provider via
 * {@code META-INF/services/com.lukarbonite.autopickup.platform.PlatformHelper}.
 *
 * <p>Will live in the {@code :fabric} module after the multi-module split.
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
