package com.lukarbonite.autopickup;

import com.lukarbonite.autopickup.core.Mending;
import com.lukarbonite.autopickup.core.Settings;
import com.lukarbonite.autopickup.platform.VersionHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Mojang-mapped entry points for the pickup logic (1.16.5 and up). Setting resolution and the
 * Mending math live in core/, so 1.12.2's MCP-mapped copy of this class shares them.
 */
public final class AutoPickupApi {

    private static final ThreadLocal<Player> blockBreaker = new ThreadLocal<>();

    public static void setBlockBreaker(Player player) { blockBreaker.set(player); }
    public static void clearBlockBreaker() { blockBreaker.remove(); }
    public static Player getBlockBreaker() { return blockBreaker.get(); }

    public static boolean isMasterEnabled(Player player) { return Settings.isMasterEnabled(player.getUUID()); }
    public static boolean isBlocksEnabled(Player player) { return Settings.isBlocksEnabled(player.getUUID()); }
    public static boolean isBlockXpEnabled(Player player) { return Settings.isBlockXpEnabled(player.getUUID()); }
    public static boolean isMobLootEnabled(Player player) { return Settings.isMobLootEnabled(player.getUUID()); }
    public static boolean isMobXpEnabled(Player player) { return Settings.isMobXpEnabled(player.getUUID()); }
    public static boolean isSplitMobLootEnabled(Player player) { return Settings.isSplitMobLootEnabled(player.getUUID()); }
    public static boolean isSplitMobXpEnabled(Player player) { return Settings.isSplitMobXpEnabled(player.getUUID()); }

    public static List<ItemStack> tryPickup(Player player, List<ItemStack> drops) {
        Level world = VersionHelper.INSTANCE.getLevel(player);
        if (world.isClientSide() || !(world instanceof ServerLevel) || player.isSpectator()
                || !isMasterEnabled(player) || !isBlocksEnabled(player)) {
            return drops;
        }
        return insertDrops(player, drops);
    }

    public static List<ItemStack> tryPickupFromMob(Player player, List<ItemStack> drops) {
        Level world = VersionHelper.INSTANCE.getLevel(player);
        if (world.isClientSide() || !(world instanceof ServerLevel) || player.isSpectator()
                || !isMasterEnabled(player) || !isMobLootEnabled(player)) {
            return drops;
        }
        return insertDrops(player, drops);
    }

    private static List<ItemStack> insertDrops(Player player, List<ItemStack> drops) {
        List<ItemStack> unpicked = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            if (VersionHelper.INSTANCE.getInventory(player).add(stack)) {
                if (!stack.isEmpty()) unpicked.add(stack);
            } else {
                unpicked.add(stack);
            }
        }
        return unpicked;
    }

    public static void tryPickupBlockExperience(Player player, int experience) {
        if (!isMasterEnabled(player) || !isBlockXpEnabled(player)) return;
        giveExperience(player, experience);
    }

    public static void tryPickupMobExperience(Player player, int experience) {
        if (!isMasterEnabled(player) || !isMobXpEnabled(player)) return;
        giveExperience(player, experience);
    }

    private static void giveExperience(Player player, int experience) {
        Level world = VersionHelper.INSTANCE.getLevel(player);
        if (experience <= 0 || world.isClientSide() || !(world instanceof ServerLevel)) return;
        ServerLevel serverLevel = (ServerLevel) world;

        VersionHelper vh = VersionHelper.INSTANCE;

        List<ItemStack> mendable = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (vh.isArmorOrHandSlot(slot)) {
                ItemStack stack = player.getItemBySlot(slot);
                if (!stack.isEmpty() && stack.isDamaged() && vh.hasMending(stack, serverLevel)) {
                    mendable.add(stack);
                }
            }
        }

        experience = Mending.repair(experience, mendable, bound -> player.getRandom().nextInt(bound),
                ItemStack::getDamageValue, ItemStack::setDamageValue);

        if (experience > 0) player.giveExperiencePoints(experience);
    }
}
