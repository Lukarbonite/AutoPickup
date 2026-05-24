package com.lukarbonite.autopickup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoPickupConfig {
    private static final Path DEFAULT_PATH = AutoPickupCommon.getConfigDir().resolve("global_config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AutoPickupConfig INSTANCE;
    private static Path currentPath = DEFAULT_PATH;

    // --- Core States ---
    public boolean autoPickup = true;
    public boolean autoPickupBlocks = true;
    public boolean autoPickupBlockXp = true;
    public boolean autoPickupMobLoot = false;
    public boolean autoPickupMobXp = false;
    public boolean autoPickupSplitMobLoot = false;
    public boolean autoPickupSplitMobXp = false;

    // --- Allowance States (Per-setting) ---
    public boolean allowMaster = true;
    public boolean allowBlocks = true;
    public boolean allowBlockXp = true;
    public boolean allowMobLoot = true;
    public boolean allowMobXp = true;
    public boolean allowSplitMobLoot = true;
    public boolean allowSplitMobXp = true;

    public static AutoPickupConfig getInstance() {
        if (INSTANCE == null) INSTANCE = new AutoPickupConfig();
        return INSTANCE;
    }

    /**
     * Called when a world/server starts. Saves any existing config, switches to
     * the per-world path (singleplayer) or the shared path (dedicated), then loads.
     */
    public static void loadForWorld(MinecraftServer server) {
        if (INSTANCE != null) INSTANCE.save();
        INSTANCE = null;

        if (server.isDedicatedServer()) {
            currentPath = DEFAULT_PATH;
        } else {
            String worldName = server.getWorldData().getLevelName()
                    .replaceAll("[^a-zA-Z0-9_\\-.]", "_");
            Path worldsDir = AutoPickupCommon.getConfigDir().resolve("worlds");
            try {
                Files.createDirectories(worldsDir);
            } catch (IOException e) {
                AutoPickupCommon.LOGGER.error("Failed to create worlds config directory", e);
            }
            currentPath = worldsDir.resolve("global_" + worldName + ".json");
        }

        getInstance().load();
    }

    public void load() {
        if (!Files.exists(currentPath)) {
            save();
            return;
        }
        try {
            String json = Files.readString(currentPath);
            INSTANCE = GSON.fromJson(json, AutoPickupConfig.class);
        } catch (IOException e) {
            AutoPickupCommon.LOGGER.error("Failed to load global config", e);
        }
    }

    public void save() {
        try {
            Files.writeString(currentPath, GSON.toJson(this));
        } catch (IOException e) {
            AutoPickupCommon.LOGGER.error("Failed to save global config", e);
        }
    }
}
