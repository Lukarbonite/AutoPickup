package com.lukarbonite.forge.platform;

import com.lukarbonite.autopickup.platform.PlatformHelper;
import net.minecraftforge.fml.common.Loader;

import java.nio.file.Path;

/**
 * Forge 1.12.2 implementation of {@link PlatformHelper}.
 * Registered as a service provider via
 * {@code META-INF/services/com.lukarbonite.autopickup.platform.PlatformHelper}.
 */
public final class ForgePlatformHelper implements PlatformHelper {

    @Override
    public Path getConfigDir() {
        return Loader.instance().getConfigDir().toPath();
    }

    @Override
    public boolean isModLoadedEarly(String modId) {
        // FML only knows the mod list once mods are discovered; before that, report not loaded.
        try {
            return Loader.isModLoaded(modId);
        } catch (Exception e) {
            return false;
        }
    }
}
