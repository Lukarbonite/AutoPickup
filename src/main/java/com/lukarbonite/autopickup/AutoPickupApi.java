package com.lukarbonite.autopickup;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public static List<ItemStack> tryPickupFromMob(PlayerEntity player, List<ItemStack> drops) {
        World world = player.getWorld();
        // Check if the world is a ServerWorld to safely access gamerules.
        if (world.isClient() || !(world instanceof ServerWorld serverWorld) || player.isSpectator()
                || !serverWorld.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_MOB_LOOT_GAMERULE_KEY)) {
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
     * Attempts to repair items with the Mending enchantment before giving the experience to the player.
     * This method replicates the logic of an experience orb being collected by a player with mending gear.
     * @param player The player picking up the experience.
     * @param experience The amount of experience picked up.
     */
    public static void tryPickupExperience(PlayerEntity player, int experience) {
        if (experience <= 0) {
            return;
        }

        // Correctly type the Optional to match the return type of getEntry()
        Optional<RegistryEntry.Reference<net.minecraft.enchantment.Enchantment>> mendingEntryOptional = player.getWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.MENDING.getValue());

        // If Mending doesn't exist for some reason, just give the XP directly.
        if (mendingEntryOptional.isEmpty()) {
            player.addExperience(experience);
            return;
        }
        RegistryEntry<net.minecraft.enchantment.Enchantment> mendingEntry = mendingEntryOptional.get();

        // Find all equipped items that are damaged and have Mending.
        // This includes armor and held items.
        List<ItemStack> mendableItems = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
                ItemStack stack = player.getEquippedStack(slot);
                if (!stack.isEmpty() && stack.isDamaged() && EnchantmentHelper.getLevel(mendingEntry, stack) > 0) {
                    mendableItems.add(stack);
                }
            }
        }

        if (mendableItems.isEmpty()) {
            // No items to mend, so give all XP to the player.
            player.addExperience(experience);
            return;
        }

        // Pick one random applicable item to repair.
        ItemStack itemToMend = mendableItems.get(player.getRandom().nextInt(mendableItems.size()));

        // In vanilla, 1 point of experience repairs 2 points of durability.
        int repairValue = Math.min(experience * 2, itemToMend.getDamage());
        itemToMend.setDamage(itemToMend.getDamage() - repairValue);

        // Calculate how much experience was actually consumed.
        // We use ceiling division (e.g., (value + 1) / 2) to ensure that repairing 1 durability costs 1 XP.
        int xpConsumed = (repairValue + 1) / 2;
        int remainingXp = experience - xpConsumed;

        // Add any leftover experience to the player's experience bar.
        if (remainingXp > 0) {
            player.addExperience(remainingXp);
        }
    }
}