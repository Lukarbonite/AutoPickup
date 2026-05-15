package com.lukarbonite.autopickup.mixin.compat.travelersbackpack;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.tiviacz.travelersbackpack.attachment.AttachmentUtils;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.inventory.StorageAccessWrapper;
import com.tiviacz.travelersbackpack.inventory.upgrades.pickup.AutoPickupUpgrade;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AutoPickupApi.class)
public class AutoPickupTBMixin {

    @Redirect(
            method = "insertDrops",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z")
    )
    private static boolean interceptInventoryAdd(Inventory inventory, ItemStack stack) {
        Player player = inventory.player;

        // Pass a copy as to not mutate the world instance if the transaction rolls back
        ItemStack remainder = insertIntoTB(player, stack.copy());

        stack.setCount(remainder.getCount());
        if (stack.isEmpty())
            return true;

        return inventory.add(stack);
    }

    @Unique
    private static ItemStack insertIntoTB(Player player, ItemStack stack) {
        BackpackWrapper wrapper = AttachmentUtils.getBackpackWrapper(player);
        if (wrapper == null)
            return stack;

        var upgradeOpt = wrapper.getUpgradeManager().getUpgrade(AutoPickupUpgrade.class);
        if (upgradeOpt.isPresent() && upgradeOpt.get().canPickup(stack)) {

            StorageAccessWrapper storage = wrapper.getStorageForInputOutput();
            ItemResource resource = ItemResource.of(stack);

            // Open a NeoForge Transfer Transaction
            try (Transaction tx = Transaction.openRoot()) {
                int remaining = stack.getCount();

                // Iterate through the backpack slots
                for (int i = 0; i < storage.size(); i++) {
                    // The wrapper handles voiding and memory slot rerouting internally during this call
                    int inserted = storage.insert(i, resource, remaining, tx);
                    remaining -= inserted;

                    if (remaining <= 0) break;
                }

                // If anything was successfully inserted, commit the transaction and shrink the item stack
                if (remaining < stack.getCount()) {
                    tx.commit();
                    stack.setCount(remaining);
                }
            }
        }
        return stack;
    }
}