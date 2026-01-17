package com.lukarbonite.autopickup;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerConfigs {
    private static final Map<UUID, ConfigData> playerSettings = new ConcurrentHashMap<>();

    // Default settings (Used before the client syncs)
    private static final ConfigData DEFAULT = new ConfigData(true, true, false, true);

    public record ConfigData(boolean master, boolean blocks, boolean mobLoot, boolean xp) {}

    public static void setPlayerConfig(UUID uuid, boolean master, boolean blocks, boolean mobLoot, boolean xp) {
        playerSettings.put(uuid, new ConfigData(master, blocks, mobLoot, xp));
        // Debug log to confirm packet reception
        // System.out.println("AutoPickup: Config synced for " + uuid);
    }

    public static void removePlayer(UUID uuid) {
        playerSettings.remove(uuid);
    }

    public static ConfigData get(UUID uuid) {
        // getOrDefault prevents NullPointerException on join
        return playerSettings.getOrDefault(uuid, DEFAULT);
    }
}