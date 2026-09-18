package com.lukarbonite.autopickup.core;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ObjIntConsumer;

/**
 * Caches experience per player and releases it after a short delay on a server tick. This ensures
 * that any related durability damage is always processed before the Mending enchantment is
 * triggered. Each version's {@code ExperienceCache} feeds it and applies the released XP.
 */
public final class XpCache {

    private XpCache() {}

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

    public static void add(UUID player, int experience) {
        activeSessions.computeIfAbsent(player, k -> new PlayerExperience()).addExperience(experience);
    }

    /** Ages every session once; sessions whose timer ran out are removed and handed to {@code release}. */
    public static void tick(ObjIntConsumer<UUID> release) {
        if (activeSessions.isEmpty()) return;

        activeSessions.forEach((uuid, session) -> {
            session.ticksUntilFinalize--;
            if (session.ticksUntilFinalize <= 0) {
                // Timer expired, means the action (or series of actions) is complete.
                activeSessions.remove(uuid);
                if (session.experience > 0) release.accept(uuid, session.experience);
            }
        });
    }
}
