package com.lukarbonite.autopickup;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A universal handler for all immediate experience gain by a player.
 * It caches experience from any source (blocks, mobs, etc.) and applies it after
 * a short delay on a server tick. This ensures that any related durability damage
 * is always processed before the Mending enchantment is triggered.
 */
public final class ExperienceCache {

    private static final ConcurrentHashMap<UUID, PlayerExperience> activeSessions = new ConcurrentHashMap<>();

    private static class PlayerExperience {
        int experience = 0;
        // A short countdown. If more experience is gained, this timer resets.
        int ticksUntilFinalize = 2;

        void addExperience(int amount) {
            this.experience += amount;
            this.ticksUntilFinalize = 2; // Reset the timer.
        }
    }

    /**
     * The single entry point for all experience that should be cached.
     * Called by mixins for block drops, mob kills, etc.
     */
    public static void add(PlayerEntity player, int experience) {
        PlayerExperience session = activeSessions.computeIfAbsent(player.getUuid(), k -> new PlayerExperience());
        session.addExperience(experience);
    }

    /**
     * A global tick handler called by ServerTickMixin to process finalized sessions.
     */
    public static void onServerTick(MinecraftServer server) {
        if (activeSessions.isEmpty()) {
            return;
        }

        activeSessions.forEach((uuid, session) -> {
            session.ticksUntilFinalize--;
            if (session.ticksUntilFinalize <= 0) {
                // Timer expired, means the action (or series of actions) is complete.
                activeSessions.remove(uuid);
                PlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null && session.experience > 0) {
                    // Apply all the collected experience at once for mending.
                    AutoPickupApi.tryPickupExperience(player, session.experience);
                }
            }
        });
    }
}