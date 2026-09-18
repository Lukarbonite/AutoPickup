package com.lukarbonite.autopickup.core;

import com.lukarbonite.autopickup.AutoPickupConfig;
import com.lukarbonite.autopickup.PlayerConfigs;

import java.util.UUID;
import java.util.function.Function;

/**
 * Resolves a player's effective settings from the admin override, their client preference and the
 * server's global config, in that order. Keyed by UUID so it has no Minecraft dependency; each
 * version's {@code AutoPickupApi} passes its player's UUID in.
 */
public final class Settings {

    private Settings() {}

    private static boolean resolve(UUID player,
                                   Function<PlayerConfigs.PlayerState, Boolean> overrideGetter,
                                   Function<PlayerConfigs.PlayerState, Boolean> clientGetter,
                                   boolean specificAllowance,
                                   boolean serverDefault) {
        PlayerConfigs.PlayerState state = PlayerConfigs.getState(player);
        Boolean override = overrideGetter.apply(state);
        if (override != null) return override;
        if (specificAllowance) return clientGetter.apply(state);
        return serverDefault;
    }

    public static boolean isMasterEnabled(UUID player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideMaster, s -> s.clientMaster, cfg.allowMaster, cfg.autoPickup);
    }
    public static boolean isBlocksEnabled(UUID player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideBlocks, s -> s.clientBlocks, cfg.allowBlocks, cfg.autoPickupBlocks);
    }
    public static boolean isBlockXpEnabled(UUID player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideBlockXp, s -> s.clientBlockXp, cfg.allowBlockXp, cfg.autoPickupBlockXp);
    }
    public static boolean isMobLootEnabled(UUID player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideMobLoot, s -> s.clientMobLoot, cfg.allowMobLoot, cfg.autoPickupMobLoot);
    }
    public static boolean isMobXpEnabled(UUID player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideMobXp, s -> s.clientMobXp, cfg.allowMobXp, cfg.autoPickupMobXp);
    }
    public static boolean isSplitMobLootEnabled(UUID player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideSplitMobLoot, s -> s.clientSplitMobLoot, cfg.allowSplitMobLoot, cfg.autoPickupSplitMobLoot);
    }
    public static boolean isSplitMobXpEnabled(UUID player) {
        AutoPickupConfig cfg = AutoPickupConfig.getInstance();
        return resolve(player, s -> s.overrideSplitMobXp, s -> s.clientSplitMobXp, cfg.allowSplitMobXp, cfg.autoPickupSplitMobXp);
    }
}
