package com.lukarbonite.fabric.mixin.compat.travelersbackpack;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.tiviacz.travelersbackpack.component.ComponentUtils;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.inventory.upgrades.pickup.AutoPickupUpgrade;
import com.tiviacz.travelersbackpack.util.InventoryHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = AutoPickupApi.class, remap = false)
public class AutoPickupTBMixin {

    @Redirect(
            method = "insertDrops",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"),
            remap = true
    )
    private static boolean interceptInventoryAdd(Inventory inventory, ItemStack stack) {
        Player player = inventory.player;

        // Pass a copy to prevent modifying the instance stored inside the backpack
        ItemStack remainder = insertIntoTB(player, stack.copy());

        // Reflect the remainder count back onto the original stack.
        // If the backpack took everything, this sets the count to 0.
        stack.setCount(remainder.getCount());

        // If the item was completely absorbed by the backpack, return true.
        if (stack.isEmpty()) {
            return true;
        }

        // Fall back to the vanilla inventory for whatever is left over.
        return inventory.add(stack);
    }

    @Unique
    private static ItemStack insertIntoTB(Player player, ItemStack stack) {
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
                return InventoryHelper.insertItemStacked(wrapper.getStorageForInputOutput(), stack, false);
            }
        }

        return stack;
    }
}