package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.core.XpCache;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;

/**
 * Caches block XP per player and applies it a moment later (see {@link XpCache}), so that any
 * related durability damage is always processed before Mending repairs it.
 */
public final class ExperienceCache {

    private ExperienceCache() {}

    public static void add(EntityPlayer player, int experience) {
        XpCache.add(player.getUniqueID(), experience);
    }

    /** Called at the end of every server tick. */
    public static void onServerTick(MinecraftServer server) {
        XpCache.tick((uuid, experience) -> {
            EntityPlayer player = server.getPlayerList().getPlayerByUUID(uuid);
            if (player != null) AutoPickupApi.tryPickupBlockExperience(player, experience);
        });
    }
}
