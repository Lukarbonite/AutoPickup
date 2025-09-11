package com.lukarbonite.autopickup;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoPickup implements ModInitializer {
    public static final String MOD_ID = "auto-pickup";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ... (gamerule definitions remain the same) ...
    public static final GameRules.Key<GameRules.BooleanRule> AUTO_PICKUP_GAMERULE_KEY =
            GameRuleRegistry.register(
                    "autoPickup",
                    GameRules.Category.PLAYER,
                    GameRuleFactory.createBooleanRule(true) // Default value is true
            );
    public static final GameRules.Key<GameRules.BooleanRule> AUTO_PICKUP_MOB_LOOT_GAMERULE_KEY =
            GameRuleRegistry.register(
                    "autoPickupMobLoot",
                    GameRules.Category.PLAYER,
                    GameRuleFactory.createBooleanRule(false) // Default value is false
            );
    public static final GameRules.Key<GameRules.BooleanRule> AUTO_PICKUP_XP_GAMERULE_KEY =
            GameRuleRegistry.register(
                    "autoPickupXp",
                    GameRules.Category.PLAYER,
                    GameRuleFactory.createBooleanRule(true) // Default value is true
            );

    @Override
    public void onInitialize() {
        LOGGER.info("Auto Pickup Mod initialized!");

        // Check if the 'architectury' API mod is loaded.
        if (FabricLoader.getInstance().isModLoaded("architectury")) {
            // If it is, enable our generalized compatibility layer.
            LOGGER.info("Architectury API found, enabling broad compatibility for Architectury-based mods.");
            initializeArchitecturyCompat();
        } else {
            // If not, our compatibility code is never run.
            LOGGER.info("Architectury API not found, compatibility layer disabled.");
        }
    }

    /**
     * This method isolates the call to the compatibility class, ensuring it is
     * only loaded if the Architectury API is present.
     */
    private void initializeArchitecturyCompat() {
        ArchitecturyCompat.register();
    }
}