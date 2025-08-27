package com.lukarbonite.autopickup;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
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

    public static void tryPickupExperience(PlayerEntity player, int experience) {
        if (experience <= 0) {
            return;
        }

        // FIX: Removed .get() because getRegistryManager().get() returns the Registry directly in this version.
        Registry<Enchantment> enchantmentRegistry = player.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        Optional<RegistryEntry.Reference<Enchantment>> mendingEntryOptional = enchantmentRegistry.getEntry(Enchantments.MENDING);

        if (mendingEntryOptional.isEmpty()) {
            player.addExperience(experience);
            return;
        }
        RegistryEntry<Enchantment> mendingEntry = mendingEntryOptional.get();

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
            player.addExperience(experience);
            return;
        }

        ItemStack itemToMend = mendableItems.get(player.getRandom().nextInt(mendableItems.size()));

        int repairValue = Math.min(experience * 2, itemToMend.getDamage());
        itemToMend.setDamage(itemToMend.getDamage() - repairValue);

        int xpConsumed = (repairValue + 1) / 2;
        int remainingXp = experience - xpConsumed;

        if (remainingXp > 0) {
            player.addExperience(remainingXp);
        }
    }
}