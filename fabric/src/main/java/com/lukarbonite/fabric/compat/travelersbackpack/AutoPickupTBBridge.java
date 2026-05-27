package com.lukarbonite.fabric.compat.travelersbackpack;

import com.tiviacz.travelersbackpack.component.ComponentUtils;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.inventory.upgrades.pickup.AutoPickupUpgrade;
import com.tiviacz.travelersbackpack.util.InventoryHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class AutoPickupTBBridge {
    public static ItemStack insertIntoTB(Player player, ItemStack stack) {
        // Grab the wrapper for the player's currently equipped backpack
        BackpackWrapper wrapper = ComponentUtils.getBackpackWrapper(player);
        if (wrapper == null) return stack;

        // Check if the backpack has the auto-pickup upgrade installed
        var upgradeOpt = wrapper.getUpgradeManager().getUpgrade(AutoPickupUpgrade.class);
        if (upgradeOpt.isPresent()) {
            AutoPickupUpgrade upgrade = upgradeOpt.get();

            // Check if the item matches the player's filter settings
            if (upgrade.canPickup(stack)) {
                // Use TB's native stacking logic to handle partial stacks and empty slots
                return InventoryHelper.insertItemStacked(wrapper.getStorage(), stack, false);
            }
        }

        return stack;
    }
}