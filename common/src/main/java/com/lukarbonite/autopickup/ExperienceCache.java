package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.core.XpCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

/**
 * A universal handler for all immediate experience gain by a player.
 * It caches experience from any source (blocks, mobs, etc.) and applies it after
 * a short delay on a server tick (see {@link XpCache}), so that any related durability
 * damage is always processed before the Mending enchantment is triggered.
 */
public final class ExperienceCache {

    private ExperienceCache() {}

    /** The single entry point for all experience that should be cached. Called by mixins for block drops. */
    public static void add(Player player, int experience) {
        XpCache.add(player.getUUID(), experience);
    }

    /** A global tick handler called by ServerTickMixin to process finalized sessions. */
    public static void onServerTick(MinecraftServer server) {
        XpCache.tick((uuid, experience) -> {
            Player player = server.getPlayerList().getPlayer(uuid);
            // Apply all the collected experience at once for mending.
            if (player != null) AutoPickupApi.tryPickupBlockExperience(player, experience);
        });
    }
}
