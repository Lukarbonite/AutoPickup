package com.lukarbonite.autopickup;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerConfigs {
    private static final Map<UUID, ConfigData> playerSettings = new ConcurrentHashMap<>();

    public record ConfigData(boolean master, boolean blocks, boolean mobLoot, boolean xp) {}

    public static void setPlayerConfig(UUID uuid, boolean master, boolean blocks, boolean mobLoot, boolean xp) {
        playerSettings.put(uuid, new ConfigData(master, blocks, mobLoot, xp));
    }

    public static void removePlayer(UUID uuid) {
        playerSettings.remove(uuid);
    }

    public static ConfigData get(UUID uuid) {
        return playerSettings.get(uuid);
    }
}