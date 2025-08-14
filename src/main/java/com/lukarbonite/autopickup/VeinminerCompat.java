package com.lukarbonite.autopickup;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A dedicated compatibility class to handle interactions with the Veinminer mod.
 * This class uses a tick-based countdown to cache and then apply all experience for
 * mending after a vein-mining operation has fully completed.
 */
public final class VeinminerCompat {

    // A map to track an active "vein mining session" for each player.
    private static final ConcurrentHashMap<UUID, VeinmineSession> activeSessions = new ConcurrentHashMap<>();

    private static class VeinmineSession {
        int experience = 0;
        // A short countdown. If another block is broken, this timer resets.
        // When it reaches zero, the session is over.
        int ticksUntilFinalize = 3;

        void addExperience(int amount) {
            this.experience += amount;
            this.ticksUntilFinalize = 3; // Reset the timer every time a block in the vein is broken.
        }
    }

    /**
     * Called by mixins when a player breaks a block that drops experience.
     * It starts a new session or adds experience to an existing one, resetting the timer.
     */
    public static void onBlockBroken(PlayerEntity player, int experience) {
        // Get or create a session for the player.
        VeinmineSession session = activeSessions.computeIfAbsent(player.getUuid(), k -> new VeinmineSession());
        session.addExperience(experience);
    }

    /**
     * A global tick handler that must be called once per server tick.
     * It decrements timers and finalizes sessions that have timed out.
     */
    public static void onServerTick(MinecraftServer server) {
        if (activeSessions.isEmpty()) {
            return;
        }

        activeSessions.forEach((uuid, session) -> {
            session.ticksUntilFinalize--;
            if (session.ticksUntilFinalize <= 0) {
                // Timer has run out, which means the vein mine is complete.
                activeSessions.remove(uuid); // Remove session first to prevent race conditions.

                PlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null && session.experience > 0) {
                    // Apply all the collected experience at once for mending.
                    AutoPickupApi.tryPickupExperience(player, session.experience);
                }
            }
        });
    }
}