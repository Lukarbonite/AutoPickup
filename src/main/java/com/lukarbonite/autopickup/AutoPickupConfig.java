package com.lukarbonite.autopickup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AutoPickupConfig {
    private static final Path CONFIG_PATH = AutoPickup.CONFIG_DIR.resolve("server_config.toml");

    // Master Toggle
    public boolean autoPickup = true;

    // Block Settings
    public boolean autoPickupBlocks = true;
    public boolean autoPickupBlockXp = true;

    // Mob Settings
    public boolean autoPickupMobLoot = false;
    public boolean autoPickupMobXp = false;

    // Splitting Logic
    public boolean autoPickupSplitMobLoot = false;
    public boolean autoPickupSplitMobXp = false;

    // Permissions
    public boolean allowClientControl = false;

    private static final AutoPickupConfig INSTANCE = new AutoPickupConfig();

    public static AutoPickupConfig getInstance() {
        return INSTANCE;
    }

    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        try {
            List<String> lines = Files.readAllLines(CONFIG_PATH);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;

                String key = parts[0].trim();
                String value = parts[1].trim();

                switch (key) {
                    case "auto_pickup" -> autoPickup = Boolean.parseBoolean(value);
                    case "auto_pickup_blocks" -> autoPickupBlocks = Boolean.parseBoolean(value);
                    case "auto_pickup_block_xp" -> autoPickupBlockXp = Boolean.parseBoolean(value);
                    case "auto_pickup_mob_loot" -> autoPickupMobLoot = Boolean.parseBoolean(value);
                    case "auto_pickup_mob_xp" -> autoPickupMobXp = Boolean.parseBoolean(value);
                    case "auto_pickup_split_mob_loot" -> autoPickupSplitMobLoot = Boolean.parseBoolean(value);
                    case "auto_pickup_split_mob_xp" -> autoPickupSplitMobXp = Boolean.parseBoolean(value);
                    case "allow_client_control" -> allowClientControl = Boolean.parseBoolean(value);
                }
            }
        } catch (IOException e) {
            AutoPickup.LOGGER.error("Failed to load server config", e);
        }
    }

    public void save() {
        List<String> lines = new ArrayList<>();
        lines.add("# AutoPickup Server Configuration");
        lines.add("# Stored in config/AutoPickup/server_config.toml");
        lines.add("");
        lines.add("auto_pickup = " + autoPickup);
        lines.add("");
        lines.add("# Block Settings");
        lines.add("auto_pickup_blocks = " + autoPickupBlocks);
        lines.add("auto_pickup_block_xp = " + autoPickupBlockXp);
        lines.add("");
        lines.add("# Mob Settings");
        lines.add("auto_pickup_mob_loot = " + autoPickupMobLoot);
        lines.add("auto_pickup_mob_xp = " + autoPickupMobXp);
        lines.add("");
        lines.add("# Splitting Settings (Shared distribution based on damage history)");
        lines.add("auto_pickup_split_mob_loot = " + autoPickupSplitMobLoot);
        lines.add("auto_pickup_split_mob_xp = " + autoPickupSplitMobXp);
        lines.add("");
        lines.add("# Permissions");
        lines.add("allow_client_control = " + allowClientControl);

        try {
            Files.write(CONFIG_PATH, lines);
        } catch (IOException e) {
            AutoPickup.LOGGER.error("Failed to save server config", e);
        }
    }
}