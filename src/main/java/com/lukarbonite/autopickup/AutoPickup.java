package com.lukarbonite.autopickup;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoPickup implements ModInitializer {
    public static final String MOD_ID = "auto-pickup";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Master gamerule. If this is false, nothing will be auto-picked up.
    public static final GameRules.Key<GameRules.BooleanRule> AUTO_PICKUP_GAMERULE_KEY =
            GameRuleRegistry.register("autoPickup", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));

    // Gamerule for block drops
    public static final GameRules.Key<GameRules.BooleanRule> AUTO_PICKUP_BLOCKS_GAMERULE_KEY =
            GameRuleRegistry.register("autoPickupBlocks", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));

    // Gamerule for mob loot.
    public static final GameRules.Key<GameRules.BooleanRule> AUTO_PICKUP_MOB_LOOT_GAMERULE_KEY =
            GameRuleRegistry.register("autoPickupMobLoot", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(false));

    // Gamerule for experience.
    public static final GameRules.Key<GameRules.BooleanRule> AUTO_PICKUP_XP_GAMERULE_KEY =
            GameRuleRegistry.register("autoPickupXp", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));

    @Override
    public void onInitialize() {
        LOGGER.info("Auto Pickup Mod initialized!");
    }
}