package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.compat.travelersbackpack.TravelersBackpackCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.enchantment.Enchantment;
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

    public static void setBlockBreaker(PlayerEntity player) { blockBreaker.set(player); }
    public static void clearBlockBreaker() { blockBreaker.remove(); }
    public static PlayerEntity getBlockBreaker() { return blockBreaker.get(); }

    // --- Helper Methods to check config permissions ---

    public static boolean isMasterEnabled(PlayerEntity player) {
        AutoPickupConfig serverConfig = AutoPickupConfig.getInstance();
        if (serverConfig.allowClientControl) {
            PlayerConfigs.ConfigData data = PlayerConfigs.get(player.getUuid());
            return data != null ? data.master() : serverConfig.autoPickup;
        }
        return serverConfig.autoPickup;
    }

    public static boolean isBlocksEnabled(PlayerEntity player) {
        AutoPickupConfig serverConfig = AutoPickupConfig.getInstance();
        if (serverConfig.allowClientControl) {
            PlayerConfigs.ConfigData data = PlayerConfigs.get(player.getUuid());
            return data != null ? data.blocks() : serverConfig.autoPickupBlocks;
        }
        return serverConfig.autoPickupBlocks;
    }

    public static boolean isMobLootEnabled(PlayerEntity player) {
        AutoPickupConfig serverConfig = AutoPickupConfig.getInstance();
        if (serverConfig.allowClientControl) {
            PlayerConfigs.ConfigData data = PlayerConfigs.get(player.getUuid());
            return data != null ? data.mobLoot() : serverConfig.autoPickupMobLoot;
        }
        return serverConfig.autoPickupMobLoot;
    }

    public static boolean isXpEnabled(PlayerEntity player) {
        AutoPickupConfig serverConfig = AutoPickupConfig.getInstance();
        if (serverConfig.allowClientControl) {
            PlayerConfigs.ConfigData data = PlayerConfigs.get(player.getUuid());
            return data != null ? data.xp() : serverConfig.autoPickupXp;
        }
        return serverConfig.autoPickupXp;
    }

    // --- Main API Methods ---

    public static List<ItemStack> tryPickup(PlayerEntity player, List<ItemStack> drops) {
        World world = player.getWorld();
        if (world.isClient() || !(world instanceof ServerWorld) || player.isSpectator()
                || !isMasterEnabled(player)
                || !isBlocksEnabled(player)) {
            return drops;
        }

        List<ItemStack> unpickedItems = new ArrayList<>();
        boolean hasTravelersBackpack = FabricLoader.getInstance().isModLoaded("travelersbackpack");

        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;

            // 1. Try Traveler's Backpack (if loaded)
            if (hasTravelersBackpack) {
                stack = TravelersBackpackCompat.tryPickup(player, stack);
                if (stack.isEmpty()) continue; // Fully picked up by backpack
            }

            // 2. Try Vanilla Inventory
            // insertStack returns true if it changed the stack (moved items).
            // We must check if the stack is empty afterwards to see if it was FULLY picked up.
            if (player.getInventory().insertStack(stack)) {
                // Some items were picked up. If any remain, add them to unpicked.
                if (!stack.isEmpty()) {
                    unpickedItems.add(stack);
                }
            } else {
                // No items were picked up (inventory full)
                unpickedItems.add(stack);
            }
        }
        return unpickedItems;
    }

    public static List<ItemStack> tryPickupFromMob(PlayerEntity player, List<ItemStack> drops) {
        World world = player.getWorld();
        if (world.isClient() || !(world instanceof ServerWorld) || player.isSpectator()
                || !isMasterEnabled(player)
                || !isMobLootEnabled(player)) {
            return drops;
        }

        List<ItemStack> unpickedItems = new ArrayList<>();
        boolean hasTravelersBackpack = FabricLoader.getInstance().isModLoaded("travelersbackpack");

        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;

            // 1. Try Traveler's Backpack (if loaded)
            if (hasTravelersBackpack) {
                stack = TravelersBackpackCompat.tryPickup(player, stack);
                if (stack.isEmpty()) continue;
            }

            // 2. Try Vanilla Inventory
            if (player.getInventory().insertStack(stack)) {
                if (!stack.isEmpty()) {
                    unpickedItems.add(stack);
                }
            } else {
                unpickedItems.add(stack);
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
        World world = player.getWorld();

        if (experience <= 0 || world.isClient() || !(world instanceof ServerWorld)
                || !isMasterEnabled(player)
                || !isXpEnabled(player)) {
            return;
        }

        // Correctly type the Optional to match the return type of getEntry()
        Optional<RegistryEntry.Reference<Enchantment>> mendingEntryOptional = player.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(Enchantments.MENDING);

        // If Mending doesn't exist for some reason, just give the XP directly.
        if (mendingEntryOptional.isEmpty()) {
            player.addExperience(experience);
            return;
        }
        RegistryEntry<Enchantment> mendingEntry = mendingEntryOptional.get();

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