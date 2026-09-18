package com.lukarbonite.autopickup.core;

import com.lukarbonite.autopickup.AutoPickupConfig;
import com.lukarbonite.autopickup.PlayerConfigs;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The behavior behind {@code /autopickup} and {@code /ap_config_sync}, without the command framework.
 * Brigadier (1.13+) and the 1.12.2 command API each wire their own {@code AutoPickupCommand} to it.
 */
public final class CommandLogic {

    private CommandLogic() {}

    // Bit flags for Boolean configs (Global & Client Sync)
    public static final int FLAG_MASTER         = 1;       // Bit 0
    public static final int FLAG_BLOCKS         = 2;       // Bit 1
    public static final int FLAG_BLOCK_XP       = 4;       // Bit 2
    public static final int FLAG_MOB_LOOT       = 8;       // Bit 3
    public static final int FLAG_MOB_XP         = 16;      // Bit 4
    public static final int FLAG_SPLIT_LOOT     = 32;      // Bit 5
    public static final int FLAG_SPLIT_XP       = 64;      // Bit 6
    public static final int FLAG_ALLOW_CLIENT   = 128;     // Bit 7 (Global only)

    public static final List<String> GLOBAL_KEYS = Collections.unmodifiableList(Arrays.asList(
            "master", "allow_master", "blocks", "allow_blocks", "blockXp", "allow_blockXp",
            "mobLoot", "allow_mobLoot", "mobXp", "allow_mobXp", "splitMobLoot", "allow_splitMobLoot",
            "splitMobXp", "allow_splitMobXp"));

    /** The per-player setting names, without "all". */
    public static final List<String> PLAYER_KEYS = Collections.unmodifiableList(Arrays.asList(
            "master", "blocks", "blockXp", "mobLoot", "mobXp", "splitMobLoot", "splitMobXp"));

    /** The keys {@code setPlayerConfig} suggests: every setting plus "all". */
    public static final List<String> PLAYER_KEY_SUGGESTIONS = Collections.unmodifiableList(Arrays.asList(
            "master", "blocks", "blockXp", "mobLoot", "mobXp", "splitMobLoot", "splitMobXp", "all"));

    public static final List<String> OVERRIDE_VALUES = Collections.unmodifiableList(Arrays.asList("true", "false", "reset"));

    /** {@code ap_config_sync}: stores the preferences a client sent as a {@code FLAG_*} bit mask. */
    public static void applyClientSync(UUID player, int mask) {
        PlayerConfigs.setClientPreference(player,
                (mask & FLAG_MASTER) != 0, (mask & FLAG_BLOCKS) != 0, (mask & FLAG_BLOCK_XP) != 0,
                (mask & FLAG_MOB_LOOT) != 0, (mask & FLAG_MOB_XP) != 0, (mask & FLAG_SPLIT_LOOT) != 0, (mask & FLAG_SPLIT_XP) != 0);
    }

    public static boolean isClientAllowed(AutoPickupConfig config, String key) {
        switch (key.toLowerCase()) {
            case "master": return config.allowMaster;
            case "blocks": return config.allowBlocks;
            case "blockxp": return config.allowBlockXp;
            case "mobloot": return config.allowMobLoot;
            case "mobxp": return config.allowMobXp;
            case "splitmobloot": return config.allowSplitMobLoot;
            case "splitmobxp": return config.allowSplitMobXp;
            default: return false;
        }
    }

    /** A player's overrides, two bits per setting (see {@link #encodeTristate}), for {@code [AP_DATA]}. */
    public static int playerOverrideMask(UUID player) {
        PlayerConfigs.PlayerState s = PlayerConfigs.getState(player);
        int mask = 0;
        mask |= encodeTristate(s.overrideMaster) << 0;
        mask |= encodeTristate(s.overrideBlocks) << 2;
        mask |= encodeTristate(s.overrideBlockXp) << 4;
        mask |= encodeTristate(s.overrideMobLoot) << 6;
        mask |= encodeTristate(s.overrideMobXp) << 8;
        mask |= encodeTristate(s.overrideSplitMobLoot) << 10;
        mask |= encodeTristate(s.overrideSplitMobXp) << 12;
        return mask;
    }

    /** The global config, a value bit and an allow bit per setting, for {@code [AP_GLOBAL]}. */
    public static int globalMask() {
        AutoPickupConfig config = AutoPickupConfig.getInstance();
        int mask = 0;

        // Pack bits in pairs to match the UI loop (i*2 = val, i*2+1 = allow)
        mask |= (config.autoPickup ? 1 : 0) << 0;
        mask |= (config.allowMaster ? 1 : 0) << 1;

        mask |= (config.autoPickupBlocks ? 1 : 0) << 2;
        mask |= (config.allowBlocks ? 1 : 0) << 3;

        mask |= (config.autoPickupBlockXp ? 1 : 0) << 4;
        mask |= (config.allowBlockXp ? 1 : 0) << 5;

        mask |= (config.autoPickupMobLoot ? 1 : 0) << 6;
        mask |= (config.allowMobLoot ? 1 : 0) << 7;

        mask |= (config.autoPickupMobXp ? 1 : 0) << 8;
        mask |= (config.allowMobXp ? 1 : 0) << 9;

        mask |= (config.autoPickupSplitMobLoot ? 1 : 0) << 10;
        mask |= (config.allowSplitMobLoot ? 1 : 0) << 11;

        mask |= (config.autoPickupSplitMobXp ? 1 : 0) << 12;
        mask |= (config.allowSplitMobXp ? 1 : 0) << 13;

        return mask;
    }

    private static int encodeTristate(Boolean b) {
        if (b == null) return 0; // Server treats both Unset and Reset as null
        return b ? 1 : 2;        // 1 = True, 2 = False
    }

    /**
     * Parses a {@code setPlayerConfig} value: true, false, or reset/unset (null, back to the server default).
     *
     * @throws IllegalArgumentException for anything else
     */
    public static Boolean parseOverride(String value) {
        switch (value.toLowerCase()) {
            case "true": return Boolean.TRUE;
            case "false": return Boolean.FALSE;
            case "reset":
            case "unset": return null;
            default: throw new IllegalArgumentException("Value must be true, false, or reset");
        }
    }

    /** Expands "all" into every setting; any other key is returned as-is. */
    public static List<String> expandPlayerKey(String key) {
        return key.equalsIgnoreCase("all") ? PLAYER_KEYS : Collections.singletonList(key);
    }

    /** Sets one global config key and saves the config. Unknown keys are saved unchanged. */
    public static void setGlobal(String key, boolean value) {
        AutoPickupConfig config = AutoPickupConfig.getInstance();

        switch (key) {
            case "master": config.autoPickup = value; break;
            case "allow_master": config.allowMaster = value; break;

            case "blocks": config.autoPickupBlocks = value; break;
            case "allow_blocks": config.allowBlocks = value; break;

            case "blockXp": config.autoPickupBlockXp = value; break;
            case "allow_blockXp": config.allowBlockXp = value; break;

            case "mobLoot": config.autoPickupMobLoot = value; break;
            case "allow_mobLoot": config.allowMobLoot = value; break;

            case "mobXp": config.autoPickupMobXp = value; break;
            case "allow_mobXp": config.allowMobXp = value; break;

            case "splitMobLoot": config.autoPickupSplitMobLoot = value; break;
            case "allow_splitMobLoot": config.allowSplitMobLoot = value; break;

            case "splitMobXp": config.autoPickupSplitMobXp = value; break;
            case "allow_splitMobXp": config.allowSplitMobXp = value; break;
        }

        config.save();
    }

    /** One line of the {@code /autopickup} status table. */
    public static final class StatusRow {
        public final String name;
        public final boolean value;
        public final boolean clientDecides;

        StatusRow(String name, boolean value, boolean clientDecides) {
            this.name = name;
            this.value = value;
            this.clientDecides = clientDecides;
        }

        /** The name padded to the table's first column, including the separator. */
        public String label() { return String.format("%-20s | ", name); }
    }

    public static final String STATUS_TITLE = "--- AutoPickup Global Configuration ---";
    public static final String STATUS_HEADER = String.format("%-20s | %s", "Feature", "Server State");
    public static final String STATUS_RULE = "---------------------------------------------";

    public static List<StatusRow> statusRows() {
        AutoPickupConfig c = AutoPickupConfig.getInstance();
        return Arrays.asList(
                new StatusRow("Master Toggle", c.autoPickup, c.allowMaster),
                new StatusRow("Blocks", c.autoPickupBlocks, c.allowBlocks),
                new StatusRow("Block XP", c.autoPickupBlockXp, c.allowBlockXp),
                new StatusRow("Mob Loot", c.autoPickupMobLoot, c.allowMobLoot),
                new StatusRow("Mob XP", c.autoPickupMobXp, c.allowMobXp),
                new StatusRow("Split Mob Loot", c.autoPickupSplitMobLoot, c.allowSplitMobLoot),
                new StatusRow("Split Mob XP", c.autoPickupSplitMobXp, c.allowSplitMobXp));
    }
}
