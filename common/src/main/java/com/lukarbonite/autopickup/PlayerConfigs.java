package com.lukarbonite.autopickup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerConfigs {
    private static final Path DEFAULT_PATH = AutoPickupCommon.getConfigDir().resolve("player_overrides.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path currentPath = DEFAULT_PATH;

    public static class PlayerState {
        // Preference: What the client requested (Transient)
        public transient boolean clientMaster = true;
        public transient boolean clientBlocks = true;
        public transient boolean clientBlockXp = true;
        public transient boolean clientMobLoot = false;
        public transient boolean clientMobXp = true;
        public transient boolean clientSplitMobLoot = false;
        public transient boolean clientSplitMobXp = false;

        // Overrides: What the admin forced (Saved)
        public Boolean overrideMaster = null;
        public Boolean overrideBlocks = null;
        public Boolean overrideBlockXp = null;
        public Boolean overrideMobLoot = null;
        public Boolean overrideMobXp = null;
        public Boolean overrideSplitMobLoot = null;
        public Boolean overrideSplitMobXp = null;
    }

    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    public static PlayerState getState(UUID uuid) {
        return STATES.computeIfAbsent(uuid, k -> new PlayerState());
    }

    /**
     * Called when a world/server starts. Saves any existing overrides, switches to
     * the per-world path (singleplayer) or the shared path (dedicated), then loads.
     */
    public static void loadForWorld(MinecraftServer server) {
        if (!STATES.isEmpty()) save();
        STATES.clear();

        if (server.isDedicatedServer()) {
            currentPath = DEFAULT_PATH;
        } else {
            String worldName = server.getWorldData().getLevelName()
                    .replaceAll("[^a-zA-Z0-9_\\-.]", "_");
            // worlds/ dir is guaranteed to exist by AutoPickupConfig.loadForWorld
            currentPath = AutoPickupCommon.getConfigDir().resolve("worlds")
                    .resolve("overrides_" + worldName + ".json");
        }

        load();
    }

    public static void setClientPreference(UUID uuid, boolean master, boolean blocks, boolean blockXp, boolean mobLoot, boolean mobXp, boolean splitMobLoot, boolean splitMobXp) {
        PlayerState state = getState(uuid);
        state.clientMaster = master;
        state.clientBlocks = blocks;
        state.clientBlockXp = blockXp;
        state.clientMobLoot = mobLoot;
        state.clientMobXp = mobXp;
        state.clientSplitMobLoot = splitMobLoot;
        state.clientSplitMobXp = splitMobXp;
    }

    public static void setAdminOverride(UUID uuid, String key, Boolean value) {
        PlayerState state = getState(uuid);
        switch (key.toLowerCase()) {
            case "autopickup", "master", "all" -> state.overrideMaster = value;
            case "blocks" -> state.overrideBlocks = value;
            case "blockxp" -> state.overrideBlockXp = value;
            case "mobloot" -> state.overrideMobLoot = value;
            case "mobxp" -> state.overrideMobXp = value;
            case "splitmobloot" -> state.overrideSplitMobLoot = value;
            case "splitmobxp" -> state.overrideSplitMobXp = value;
        }
        save();
    }

    public static void load() {
        if (!Files.exists(currentPath)) return;
        try (Reader reader = Files.newBufferedReader(currentPath)) {
            Type type = new TypeToken<Map<UUID, PlayerState>>(){}.getType();
            Map<UUID, PlayerState> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                STATES.putAll(loaded);
            }
        } catch (IOException e) {
            AutoPickupCommon.LOGGER.error("Failed to load player overrides", e);
        }
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(currentPath)) {
            GSON.toJson(STATES, writer);
        } catch (IOException e) {
            AutoPickupCommon.LOGGER.error("Failed to save player overrides", e);
        }
    }
}
