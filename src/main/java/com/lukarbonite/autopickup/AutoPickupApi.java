package com.lukarbonite.autopickup;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * API for other mods to interact with AutoPickup.
 */
public final class AutoPickupApi {

    // A ThreadLocal to hold the player who is breaking a block.
    // This allows us to pass this context to deeper method calls without changing method signatures.
    private static final ThreadLocal<PlayerEntity> blockBreaker = new ThreadLocal<>();

    /**
     * Sets the player for the current thread's block-breaking context.
     * @param player The player breaking the block.
     */
    public static void setBlockBreaker(PlayerEntity player) {
        blockBreaker.set(player);
    }

    /**
     * Clears the player from the current thread's block-breaking context.
     */
    public static void clearBlockBreaker() {
        blockBreaker.remove();
    }

    /**
     * Gets the player from the current thread's block-breaking context.
     * @return The player, or null if not set.
     */
    public static PlayerEntity getBlockBreaker() {
        return blockBreaker.get();
    }


    /**
     * Attempts to add a list of item stacks directly to the player's inventory.
     * This is the primary integration point for mods with custom block-breaking logic.
     * It respects the 'autoPickup' gamerule.
     *
     * @param player The player who should receive the items.
     * @param drops  The list of item stacks to give.
     * @return A list of item stacks that could not be added to the player's inventory (e.g., inventory was full).
     *         Returns the original list if pickup is not possible (spectator, gamerule off, or not on server).
     */
    public static List<ItemStack> tryPickup(PlayerEntity player, List<ItemStack> drops) {
        World world = player.getWorld();

        // Gamerules are a server-side concept. If we are on the client, or the world is not
        // a ServerWorld for some reason, we cannot check the gamerule. In this case, we
        // return the original drops to be handled by the caller (e.g., dropped in the world).
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return drops;
        }

        // Now that we have a 'serverWorld' variable, we can safely access getGameRules().
        // Spectators also cannot pick up items.
        if (player.isSpectator() || !serverWorld.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)) {
            return drops;
        }

        List<ItemStack> unpickedItems = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (!stack.isEmpty()) {
                // Attempt to insert the stack into the player's inventory.
                // A copy is used to prevent modifying the original stack which might be needed.
                if (!player.getInventory().insertStack(stack.copy())) {
                    // If it fails, add the original stack to the list of unpicked items.
                    unpickedItems.add(stack);
                }
            }
        }
        return unpickedItems;
    }
}