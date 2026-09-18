package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.core.Mending;
import com.lukarbonite.autopickup.core.Settings;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;

/**
 * MCP-mapped entry points for the pickup logic on 1.12.2. Mirrors common/'s Mojang-mapped
 * {@code AutoPickupApi}; setting resolution and the Mending math are shared through core/.
 */
public final class AutoPickupApi {

    private static final ThreadLocal<EntityPlayer> blockBreaker = new ThreadLocal<>();

    public static void setBlockBreaker(EntityPlayer player) { blockBreaker.set(player); }
    public static void clearBlockBreaker() { blockBreaker.remove(); }
    public static EntityPlayer getBlockBreaker() { return blockBreaker.get(); }

    public static boolean isMasterEnabled(EntityPlayer player) { return Settings.isMasterEnabled(player.getUniqueID()); }
    public static boolean isBlocksEnabled(EntityPlayer player) { return Settings.isBlocksEnabled(player.getUniqueID()); }
    public static boolean isBlockXpEnabled(EntityPlayer player) { return Settings.isBlockXpEnabled(player.getUniqueID()); }
    public static boolean isMobLootEnabled(EntityPlayer player) { return Settings.isMobLootEnabled(player.getUniqueID()); }
    public static boolean isMobXpEnabled(EntityPlayer player) { return Settings.isMobXpEnabled(player.getUniqueID()); }
    public static boolean isSplitMobLootEnabled(EntityPlayer player) { return Settings.isSplitMobLootEnabled(player.getUniqueID()); }
    public static boolean isSplitMobXpEnabled(EntityPlayer player) { return Settings.isSplitMobXpEnabled(player.getUniqueID()); }

    public static List<ItemStack> tryPickup(EntityPlayer player, List<ItemStack> drops) {
        World world = player.world;
        if (world.isRemote || !(world instanceof WorldServer) || player.isSpectator()
                || !isMasterEnabled(player) || !isBlocksEnabled(player)) {
            return drops;
        }
        return insertDrops(player, drops);
    }

    public static List<ItemStack> tryPickupFromMob(EntityPlayer player, List<ItemStack> drops) {
        World world = player.world;
        if (world.isRemote || !(world instanceof WorldServer) || player.isSpectator()
                || !isMasterEnabled(player) || !isMobLootEnabled(player)) {
            return drops;
        }
        return insertDrops(player, drops);
    }

    private static List<ItemStack> insertDrops(EntityPlayer player, List<ItemStack> drops) {
        List<ItemStack> unpicked = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            // Like Inventory.add on newer versions: true if anything was stored, shrinking the stack.
            if (player.inventory.addItemStackToInventory(stack)) {
                if (!stack.isEmpty()) unpicked.add(stack);
            } else {
                unpicked.add(stack);
            }
        }
        return unpicked;
    }

    public static void tryPickupBlockExperience(EntityPlayer player, int experience) {
        if (!isMasterEnabled(player) || !isBlockXpEnabled(player)) return;
        giveExperience(player, experience);
    }

    public static void tryPickupMobExperience(EntityPlayer player, int experience) {
        if (!isMasterEnabled(player) || !isMobXpEnabled(player)) return;
        giveExperience(player, experience);
    }

    private static void giveExperience(EntityPlayer player, int experience) {
        if (experience <= 0 || player.world.isRemote) return;

        List<ItemStack> mendable = new ArrayList<>();
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            ItemStack stack = player.getItemStackFromSlot(slot);
            if (!stack.isEmpty() && stack.isItemDamaged()
                    && EnchantmentHelper.getEnchantmentLevel(Enchantments.MENDING, stack) > 0) {
                mendable.add(stack);
            }
        }

        experience = Mending.repair(experience, mendable, bound -> player.getRNG().nextInt(bound),
                ItemStack::getItemDamage, ItemStack::setItemDamage);

        if (experience > 0) player.addExperience(experience);
    }
}
