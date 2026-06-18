package com.lukarbonite.autopickup.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ServiceLoader;

public interface VersionHelper {

    VersionHelper INSTANCE = ServiceLoader.load(VersionHelper.class)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No VersionHelper implementation found"));

    /** True if the stack has the Mending enchantment. */
    boolean hasMending(ItemStack stack, ServerLevel level);

    /** True if this slot should be checked when distributing Mending XP. */
    boolean isArmorOrHandSlot(EquipmentSlot slot);

    /** True if the player is an operator on the server. */
    boolean isOp(ServerPlayer player, MinecraftServer server);
}
