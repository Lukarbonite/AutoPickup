package com.lukarbonite.autopickup.platform;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

public class VersionHelperImpl implements VersionHelper {

    @Override
    public boolean hasMending(ItemStack stack, ServerLevel level) {
        return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, stack) > 0;
    }

    @Override
    public boolean isArmorOrHandSlot(EquipmentSlot slot) {
        return slot.getType() == EquipmentSlot.Type.ARMOR
                || slot == EquipmentSlot.MAINHAND
                || slot == EquipmentSlot.OFFHAND;
    }

    @Override
    public boolean isOp(ServerPlayer player, MinecraftServer server) {
        return server.getPlayerList().isOp(player.getGameProfile());
    }

    @Override
    public Level getLevel(Entity entity) {
        return entity.level;
    }

    @Override
    public Inventory getInventory(Player player) {
        return player.inventory;
    }

    @Override
    public MutableComponent literal(String text) {
        return new TextComponent(text);
    }

    @Override
    public void sendSuccess(CommandSourceStack source, Component message, boolean broadcastToOps) {
        source.sendSuccess(message, broadcastToOps);
    }
}
