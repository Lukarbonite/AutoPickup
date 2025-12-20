package com.lukarbonite.autopickup;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AutoPickupConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("autopickup.toml");

    public boolean autoPickup = true;
    public boolean autoPickupBlocks = true;
    public boolean autoPickupMobLoot = false;
    public boolean autoPickupXp = true;
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
                    case "auto_pickup_mob_loot" -> autoPickupMobLoot = Boolean.parseBoolean(value);
                    case "auto_pickup_xp" -> autoPickupXp = Boolean.parseBoolean(value);
                    case "allow_client_control" -> allowClientControl = Boolean.parseBoolean(value);
                }
            }
        } catch (IOException e) {
            AutoPickup.LOGGER.error("Failed to load config", e);
        }
    }

    public void save() {
        List<String> lines = new ArrayList<>();
        lines.add("# AutoPickup Configuration");
        lines.add("");
        lines.add("auto_pickup = " + autoPickup);
        lines.add("auto_pickup_blocks = " + autoPickupBlocks);
        lines.add("auto_pickup_mob_loot = " + autoPickupMobLoot);
        lines.add("auto_pickup_xp = " + autoPickupXp);
        lines.add("allow_client_control = " + allowClientControl);

        try {
            Files.write(CONFIG_PATH, lines);
        } catch (IOException e) {
            AutoPickup.LOGGER.error("Failed to save config", e);
        }
    }
}