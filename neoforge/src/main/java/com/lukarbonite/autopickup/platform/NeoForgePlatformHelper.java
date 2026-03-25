package com.lukarbonite.autopickup.platform;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import com.lukarbonite.autopickup.client.NeoForgeClientConfig;


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
    public boolean isModLoadedEarly(String modId) {
        // During Mixin stage, we must use the LoadingModList
        // or check the ModFile discovery directly.
        try {
            return FMLLoader.getCurrent().getLoadingModList() != null &&
                    FMLLoader.getCurrent().getLoadingModList().getModFileById(modId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void updateClientAllowances(boolean[] allowances) {
        NeoForgeClientConfig.allowMaster = allowances[0];
        NeoForgeClientConfig.allowBlocks = allowances[1];
        NeoForgeClientConfig.allowBlockXp = allowances[2];
        NeoForgeClientConfig.allowMobLoot = allowances[3];
        NeoForgeClientConfig.allowMobXp = allowances[4];
        NeoForgeClientConfig.allowSplitMobLoot = allowances[5];
        NeoForgeClientConfig.allowSplitMobXp = allowances[6];
    }
}
