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

    @Override
    public void updateClientAllowances(boolean[] allowances) {
        ClientConfigManager.allowMaster = allowances[0];
        ClientConfigManager.allowBlocks = allowances[1];
        ClientConfigManager.allowBlockXp = allowances[2];
        ClientConfigManager.allowMobLoot = allowances[3];
        ClientConfigManager.allowMobXp = allowances[4];
        ClientConfigManager.allowSplitMobLoot = allowances[5];
        ClientConfigManager.allowSplitMobXp = allowances[6];
    }
}
