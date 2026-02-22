package com.lukarbonite.autopickup;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoPickupConfig {
    private static final Path PATH = AutoPickup.CONFIG_DIR.resolve("global_config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AutoPickupConfig INSTANCE;

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

    public void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try {
            String json = Files.readString(PATH);
            INSTANCE = GSON.fromJson(json, AutoPickupConfig.class);
        } catch (IOException e) {
            AutoPickup.LOGGER.error("Failed to load global config", e);
        }
    }

    public void save() {
        try {
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            AutoPickup.LOGGER.error("Failed to save global config", e);
        }
    }
}