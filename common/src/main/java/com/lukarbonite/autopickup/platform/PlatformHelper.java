package com.lukarbonite.autopickup.platform;

import java.nio.file.Path;
import java.util.ServiceLoader;

/**
 * SPI abstraction for platform-specific (Fabric / NeoForge) services.
 *
 * <p>Each platform module provides exactly one implementation and registers it via
 * {@code META-INF/services/com.lukarbonite.autopickup.platform.PlatformHelper}.
 *
 * <p>{@link #get()} resolves the implementation on first call using {@link ServiceLoader},
 * which means it is safe to call from {@code IMixinConfigPlugin.onLoad()} — before the
 * mod entry point has executed.
 */
public interface PlatformHelper {

    /** Returns the loader's config directory (e.g. {@code .minecraft/config}). */
    Path getConfigDir();

    /**
     * A safe version of isModLoaded to be used during the Mixin stage.
     */
    boolean isModLoadedEarly(String modId);

    // --- Singleton accessor (lazy-loaded via ServiceLoader) ---

    PlatformHelper[] INSTANCE = {null};

    /**
     * Returns the platform implementation.
     * On the first call the implementation is discovered via {@link ServiceLoader};
     * subsequent calls return the cached instance.
     */
    static PlatformHelper get() {
        if (INSTANCE[0] == null) {
            INSTANCE[0] = ServiceLoader.load(PlatformHelper.class)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No PlatformHelper implementation found on the classpath. " +
                            "Ensure the platform JAR provides META-INF/services/" +
                            "com.lukarbonite.autopickup.platform.PlatformHelper"));
        }
        return INSTANCE[0];
    }

    /** * Updates the platform-specific client configuration with server-provided allowances.
     */
    void updateClientAllowances(boolean[] allowances);
}
