package com.lukarbonite.autopickup.client;

import com.lukarbonite.autopickup.AutoPickup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ClientConfigManager {
    private static final Path PRESETS_DIR = AutoPickup.CONFIG_DIR.resolve("presets");
    private static final Path DEFAULT_CONFIG = AutoPickup.CONFIG_DIR.resolve("client_default.toml");

    private static final ClientProfile activeProfile = new ClientProfile();
    private static Path currentFilePath = DEFAULT_CONFIG;

    public static boolean allowMaster = true;
    public static boolean allowBlocks = true;
    public static boolean allowBlockXp = true;
    public static boolean allowMobLoot = true;
    public static boolean allowMobXp = true;
    public static boolean allowSplitMobLoot = true;
    public static boolean allowSplitMobXp = true;

    public static class ClientProfile {
        public boolean master = true;
        public boolean blocks = true;
        public boolean blockXp = true;
        public boolean mobLoot = false;
        public boolean mobXp = false;
        public boolean splitMobLoot = false;
        public boolean splitMobXp = false;
    }

    static {
        try { Files.createDirectories(PRESETS_DIR); } catch (IOException e) { e.printStackTrace(); }
        load(DEFAULT_CONFIG);
    }

    public static void updateConnection() {
        Minecraft client = Minecraft.getInstance();
        String id;
        if (client.isLocalServer() && client.getSingleplayerServer() != null) {
            id = "sp_" + client.getSingleplayerServer().getWorldData().getLevelName();
        } else if (client.getCurrentServer() != null) {
            id = "mp_" + client.getCurrentServer().ip.replace(":", "_");
        } else {
            currentFilePath = DEFAULT_CONFIG;
            load(currentFilePath);
            return;
        }
        id = id.replaceAll("[^a-zA-Z0-9_\\-.]", "_");
        currentFilePath = PRESETS_DIR.resolve(id + ".toml");
        load(currentFilePath);
    }

    public static void load(Path path) {
        if (!Files.exists(path)) {
            if (!path.equals(DEFAULT_CONFIG) && Files.exists(DEFAULT_CONFIG)) {
                load(DEFAULT_CONFIG);
                save();
            } else {
                save();
            }
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;
                String key = parts[0].trim();
                String val = parts[1].trim();
                switch (key) {
                    case "master" -> activeProfile.master = Boolean.parseBoolean(val);
                    case "blocks" -> activeProfile.blocks = Boolean.parseBoolean(val);
                    case "blockXp" -> activeProfile.blockXp = Boolean.parseBoolean(val);
                    case "mobLoot" -> activeProfile.mobLoot = Boolean.parseBoolean(val);
                    case "mobXp" -> activeProfile.mobXp = Boolean.parseBoolean(val);
                    case "splitMobLoot" -> activeProfile.splitMobLoot = Boolean.parseBoolean(val);
                    case "splitMobXp" -> activeProfile.splitMobXp = Boolean.parseBoolean(val);
                }
            }
        } catch (IOException e) { AutoPickup.LOGGER.error("Failed to load client config", e); }
    }

    public static void save() {
        List<String> lines = new ArrayList<>();
        lines.add("# AutoPickup Client Configuration");
        lines.add("master = " + activeProfile.master);
        lines.add("blocks = " + activeProfile.blocks);
        lines.add("blockXp = " + activeProfile.blockXp);
        lines.add("mobLoot = " + activeProfile.mobLoot);
        lines.add("mobXp = " + activeProfile.mobXp);
        lines.add("splitMobLoot = " + activeProfile.splitMobLoot);
        lines.add("splitMobXp = " + activeProfile.splitMobXp);
        try { Files.write(currentFilePath, lines); } catch (IOException e) { e.printStackTrace(); }
    }

    public static ClientProfile getProfile() { return activeProfile; }

    public static boolean isMaster() { return activeProfile.master; }
    public static boolean isBlocks() { return activeProfile.blocks; }
    public static boolean isBlockXp() { return activeProfile.blockXp; }
    public static boolean isMobLoot() { return activeProfile.mobLoot; }
    public static boolean isMobXp() { return activeProfile.mobXp; }
    public static boolean isSplitMobLoot() { return activeProfile.splitMobLoot; }
    public static boolean isSplitMobXp() { return activeProfile.splitMobXp; }
}