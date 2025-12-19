package com.lukarbonite.autopickup;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

public final class AutoPickupApi {

    private static final ThreadLocal<Player> blockBreaker = new ThreadLocal<>();

    public static void setBlockBreaker(Player player) {
        blockBreaker.set(player);
    }

    public static void clearBlockBreaker() {
        blockBreaker.remove();
    }

    public static Player getBlockBreaker() {
        return blockBreaker.get();
    }

    public static List<ItemStack> tryPickup(Player player, List<ItemStack> drops) {
        Level world = player.level();
        // Check master rule first, then the specific block rule.
        if (world.isClientSide() || !(world instanceof ServerLevel serverWorld) || player.isSpectator()
                || !serverWorld.getGameRules().get(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)
                || !serverWorld.getGameRules().get(AutoPickup.AUTO_PICKUP_BLOCKS_GAMERULE_KEY)) {
            return drops;
        }
        List<ItemStack> unpickedItems = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (!stack.isEmpty()) {
                if (!player.getInventory().add(stack)) {
                    unpickedItems.add(stack);
                }
            }
        }
        return unpickedItems;
    }

    public static List<ItemStack> tryPickupFromMob(Player player, List<ItemStack> drops) {
        Level world = player.level();
        // Check master rule first, then the specific mob loot rule.
        if (world.isClientSide() || !(world instanceof ServerLevel serverWorld) || player.isSpectator()
                || !serverWorld.getGameRules().get(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)
                || !serverWorld.getGameRules().get(AutoPickup.AUTO_PICKUP_MOB_LOOT_GAMERULE_KEY)) {
            return drops;
        }
        List<ItemStack> unpickedItems = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (!stack.isEmpty()) {
                if (!player.getInventory().add(stack)) {
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
    public static void tryPickupExperience(Player player, int experience) {
        Level world = player.level();
        // Check master rule first, then the specific XP rule.
        if (experience <= 0 || world.isClientSide() || !(world instanceof ServerLevel serverWorld)
                || !serverWorld.getGameRules().get(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)
                || !serverWorld.getGameRules().get(AutoPickup.AUTO_PICKUP_XP_GAMERULE_KEY)) {
            return;
        }

        // Correctly type the Optional to match the return type of getEntry()
        Optional<Holder.Reference<net.minecraft.world.item.enchantment.Enchantment>> mendingEntryOptional = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.MENDING.identifier());

        // If Mending doesn't exist for some reason, just give the XP directly.
        if (mendingEntryOptional.isEmpty()) {
            player.giveExperiencePoints(experience);
            return;
        }
        Holder<net.minecraft.world.item.enchantment.Enchantment> mendingEntry = mendingEntryOptional.get();

        // Find all equipped items that are damaged and have Mending.
        // This includes armor and held items.
        List<ItemStack> mendableItems = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
                ItemStack stack = player.getItemBySlot(slot);
                if (!stack.isEmpty() && stack.isDamaged() && EnchantmentHelper.getItemEnchantmentLevel(mendingEntry, stack) > 0) {
                    mendableItems.add(stack);
                }
            }
        }

        if (mendableItems.isEmpty()) {
            // No items to mend, so give all XP to the player.
            player.giveExperiencePoints(experience);
            return;
        }

        // Pick one random applicable item to repair.
        ItemStack itemToMend = mendableItems.get(player.getRandom().nextInt(mendableItems.size()));

        // In vanilla, 1 point of experience repairs 2 points of durability.
        int repairValue = Math.min(experience * 2, itemToMend.getDamageValue());
        itemToMend.setDamageValue(itemToMend.getDamageValue() - repairValue);

        // Calculate how much experience was actually consumed.
        // We use ceiling division (e.g., (value + 1) / 2) to ensure that repairing 1 durability costs 1 XP.
        int xpConsumed = (repairValue + 1) / 2;
        int remainingXp = experience - xpConsumed;

        // Add any leftover experience to the player's experience bar.
        if (remainingXp > 0) {
            player.giveExperiencePoints(remainingXp);
        }
    }
}