package com.lukarbonite.autopickup;

import net.minecraft.server.MinecraftServer;

/** Switches the global config and player overrides to the files for the world a server is running. */
public final class WorldConfigs {

    private WorldConfigs() {}

    /** Call when a server has started: a shared file on dedicated servers, per-world files in singleplayer. */
    public static void load(MinecraftServer server) {
        boolean dedicated = server.isDedicatedServer();
        String levelName = server.getEntityWorld().getWorldInfo().getWorldName();
        AutoPickupConfig.loadForWorld(dedicated, levelName);
        PlayerConfigs.loadForWorld(dedicated, levelName);
    }
}
