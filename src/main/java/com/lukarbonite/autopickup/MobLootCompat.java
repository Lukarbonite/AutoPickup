package com.lukarbonite.autopickup;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A dedicated compatibility class to handle the timing of mob loot experience.
 * It caches experience dropped from a mob kill and applies it at the end of the tick,
 * ensuring that weapon durability damage is processed before Mending is applied.
 */
public final class MobLootCompat {

    // A map to store pending experience for each player for the current tick.
    private static final ConcurrentHashMap<UUID, Integer> pendingExperience = new ConcurrentHashMap<>();

    /**
     * Caches the experience amount for a specific player.
     * This is called by the MobLootMixin instead of applying the XP immediately.
     */
    public static void cacheExperience(PlayerEntity player, int amount) {
        // The merge function cleanly adds the amount to an existing entry or creates a new one.
        pendingExperience.merge(player.getUuid(), amount, Integer::sum);
    }

    /**
     * Processes all cached experience at the end of a server tick.
     * This method is called from our ServerTickMixin.
     */
    public static void onServerTickEnd(MinecraftServer server) {
        if (pendingExperience.isEmpty()) {
            return;
        }

        // Process experience for each player in the cache.
        pendingExperience.forEach((uuid, experience) -> {
            PlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null && experience > 0) {
                // Now that the tick is over, apply the experience for mending.
                AutoPickupApi.tryPickupExperience(player, experience);
            }
        });

        // Clear the cache for the next tick.
        pendingExperience.clear();
    }
}