package com.lukarbonite.autopickup.compat.travelersbackpack;

import com.tiviacz.travelersbackpack.attachment.AttachmentUtils;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.inventory.upgrades.pickup.AutoPickupUpgrade;
import com.tiviacz.travelersbackpack.util.InventoryHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class AutoPickupTBBridge {
    public static ItemStack insertIntoTB(Player player, ItemStack stack) {
        BackpackWrapper wrapper = AttachmentUtils.getBackpackWrapper(player);
        if (wrapper == null) return stack;

        var upgradeOpt = wrapper.getUpgradeManager().getUpgrade(AutoPickupUpgrade.class);
        if (upgradeOpt.isPresent() && upgradeOpt.get().canPickup(stack)) {
            return InventoryHelper.insertItemStacked(wrapper.getStorageForInputOutput(), stack, false);
        }

        return stack;
    }
}
