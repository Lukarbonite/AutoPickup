package com.lukarbonite.autopickup;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoPickup implements ModInitializer {
    public static final String MOD_ID = "auto-pickup";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Master gamerule. If this is false, nothing will be auto-picked up.
    public static final GameRule<Boolean> AUTO_PICKUP_GAMERULE_KEY =
            GameRuleBuilder.forBoolean(true)
                    .category(GameRuleCategory.PLAYER)
                    .buildAndRegister(Identifier.fromNamespaceAndPath(MOD_ID, "auto_pickup"));

    // Gamerule for block drops
    public static final GameRule<Boolean> AUTO_PICKUP_BLOCKS_GAMERULE_KEY =
            GameRuleBuilder.forBoolean(true)
                    .category(GameRuleCategory.PLAYER)
                    .buildAndRegister(Identifier.fromNamespaceAndPath(MOD_ID, "auto_pickup_blocks"));

    // Gamerule for mob loot.
    public static final GameRule<Boolean> AUTO_PICKUP_MOB_LOOT_GAMERULE_KEY =
            GameRuleBuilder.forBoolean(false)
                    .category(GameRuleCategory.PLAYER)
                    .buildAndRegister(Identifier.fromNamespaceAndPath(MOD_ID, "auto_pickup_mob_loot"));

    // Gamerule for experience.
    public static final GameRule<Boolean> AUTO_PICKUP_XP_GAMERULE_KEY =
            GameRuleBuilder.forBoolean(true)
                    .category(GameRuleCategory.PLAYER)
                    .buildAndRegister(Identifier.fromNamespaceAndPath(MOD_ID, "auto_pickup_xp"));

    @Override
    public void onInitialize() {
        LOGGER.info("Auto Pickup Mod initialized!");
    }
}