package com.lukarbonite.autopickup.platform;

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
}
