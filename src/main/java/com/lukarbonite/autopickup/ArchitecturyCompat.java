package com.lukarbonite.autopickup;

import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.BlockEvent;

/**
 * Provides generalized compatibility for any mod using the Architectury API's
 * block break events. This is a more robust alternative to mod-specific mixins.
 */
public class ArchitecturyCompat {

    public static void register() {
        // This event is fired by Architectury just before a block is broken by a player.
        // It's used by mods like Liteminer, but this listener will work for any mod
        // that uses this standard event.
        BlockEvent.BREAK.register((world, pos, state, player, experience) -> {
            if (world.isClient() || player == null) {
                return EventResult.pass();
            }

            // We no longer check for a specific mod. If this event fires, we assume
            // the player is responsible for any non-standard block breaks this tick.
            // This sets the context needed by our ServerWorldMixin and BlockDropExperienceMixin.
            AutoPickupApi.setBlockBreaker(player);

            return EventResult.pass();
        });
    }
}