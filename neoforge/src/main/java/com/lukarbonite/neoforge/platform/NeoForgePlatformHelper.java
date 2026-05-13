package com.lukarbonite.neoforge.platform;

import com.lukarbonite.autopickup.platform.PlatformHelper;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * NeoForge implementation of {@link PlatformHelper}.
 * Registered as a service provider via
 * META-INF/services/com.lukarbonite.autopickup.platform.PlatformHelper.
 */
public final class NeoForgePlatformHelper implements PlatformHelper {

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoadedEarly(String modId) {
        try {
            return FMLLoader.getLoadingModList() != null &&
                    FMLLoader.getLoadingModList().getModFileById(modId) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
