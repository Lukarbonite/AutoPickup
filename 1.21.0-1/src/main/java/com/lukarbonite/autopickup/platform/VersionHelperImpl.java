package com.lukarbonite.autopickup.platform;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public class VersionHelperImpl implements VersionHelper {

    @Override
    public boolean hasMending(ItemStack stack, ServerLevel level) {
        Holder<Enchantment> mending = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.MENDING);
        return EnchantmentHelper.getItemEnchantmentLevel(mending, stack) > 0;
    }

    @Override
    public boolean isArmorOrHandSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET
                || slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
    }

    @Override
    public boolean isOp(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        return server.getPlayerList().isOp(player.getGameProfile());
    }

    @Override
    public Level getLevel(Entity entity) {
        return entity.level();
    }

    @Override
    public Inventory getInventory(Player player) {
        return player.getInventory();
    }

    @Override
    public MutableComponent literal(String text) {
        return Component.literal(text);
    }

    @Override
    public void sendSuccess(CommandSourceStack source, Component message, boolean broadcastToOps) {
        source.sendSuccess(() -> message, broadcastToOps);
    }
}
