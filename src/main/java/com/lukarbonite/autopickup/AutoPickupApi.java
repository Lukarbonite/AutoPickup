package com.lukarbonite.autopickup;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class AutoPickupApi {

    private static final ThreadLocal<PlayerEntity> blockBreaker = new ThreadLocal<>();

    public static void setBlockBreaker(PlayerEntity player) {
        blockBreaker.set(player);
    }

    public static void clearBlockBreaker() {
        blockBreaker.remove();
    }

    public static PlayerEntity getBlockBreaker() {
        return blockBreaker.get();
    }

    /**
     * Attempts to add item stacks to a player's inventory based on the autoPickup gamerule for blocks.
     */
    public static List<ItemStack> tryPickup(PlayerEntity player, List<ItemStack> drops) {
        World world = player.getWorld();

        if (world.isClient() || !(world instanceof ServerWorld serverWorld) || player.isSpectator()
                || !serverWorld.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)) {
            return drops;
        }

        List<ItemStack> unpickedItems = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (!stack.isEmpty()) {
                if (!player.getInventory().insertStack(stack)) {
                    unpickedItems.add(stack);
                }
            }
        }
        return unpickedItems;
    }

    /**
     * Attempts to add item stacks to a player's inventory based on the autoPickupMobLoot gamerule.
     * This is intended for mob drops.
     */
    public static List<ItemStack> tryPickupFromMob(PlayerEntity player, List<ItemStack> drops) {
        World world = player.getWorld();

        if (world.isClient() || !(world instanceof ServerWorld serverWorld) || player.isSpectator()
                || !serverWorld.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_MOB_LOOT_GAMERULE_KEY)) {
            return drops;
        }

        List<ItemStack> unpickedItems = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (!stack.isEmpty()) {
                // player.getInventory().insertStack() modifies the stack passed to it.
                // If the stack cannot be fully inserted, it returns false and the stack
                // object contains the remainder.
                if (!player.getInventory().insertStack(stack)) {
                    unpickedItems.add(stack);
                }
            }
        }
        return unpickedItems;
    }
}