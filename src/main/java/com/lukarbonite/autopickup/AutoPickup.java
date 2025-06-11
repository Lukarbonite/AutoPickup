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

	// Gamerule definition
	public static final GameRules.Key<GameRules.BooleanRule> AUTO_PICKUP_GAMERULE_KEY =
			GameRuleRegistry.register(
					"autoPickup",
					GameRules.Category.PLAYER, // Or GameRules.Category.MISC
					GameRuleFactory.createBooleanRule(true) // Default value is true
			);

	@Override
	public void onInitialize() {
		LOGGER.info("Auto Pickup Mod initialized!");
		// The gamerule is registered via the static initializer of the Key.
		// No further action needed here for registration.
	}
}