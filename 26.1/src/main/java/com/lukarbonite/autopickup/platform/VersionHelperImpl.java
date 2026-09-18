package com.lukarbonite.autopickup.platform;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class VersionHelperImpl implements VersionHelper {

    @Override
    public boolean hasMending(ItemStack stack, ServerLevel level) {
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null) return false;
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.is(Enchantments.MENDING)) return true;
        }
        return false;
    }

    @Override
    public boolean isArmorOrHandSlot(EquipmentSlot slot) {
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                || slot == EquipmentSlot.MAINHAND
                || slot == EquipmentSlot.OFFHAND;
    }

    @Override
    public boolean isOp(ServerPlayer player, net.minecraft.server.MinecraftServer server) {
        return server.getPlayerList().isOp(player.nameAndId());
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
