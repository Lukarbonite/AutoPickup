package com.lukarbonite.forge.mixin.compat.travelersbackpack;

import com.tiviacz.travelersbackpack.capability.AttachmentUtils;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.inventory.upgrades.pickup.AutoPickupUpgrade;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class AutoPickupTBBridge {
    public static ItemStack insertIntoTB(Player player, ItemStack stack) {
        BackpackWrapper wrapper = AttachmentUtils.getBackpackWrapper(player);
        if (wrapper == null) return stack;

        var upgradeOpt = wrapper.getUpgradeManager().pickupUpgrade;
        if (upgradeOpt.isPresent() && upgradeOpt.get().canPickup(stack)) {
            var storage = wrapper.getStorage();
            int slots = storage.getSlots();
            for (int i = 0; i < slots; i++) {
                stack = storage.insertItem(i, stack, false);
                if (stack.isEmpty()) break;
            }
        }
        return stack;
    }
}