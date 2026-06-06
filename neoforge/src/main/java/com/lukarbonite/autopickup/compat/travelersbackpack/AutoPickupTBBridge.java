package com.lukarbonite.autopickup.compat.travelersbackpack;


import com.tiviacz.travelersbackpack.capability.AttachmentUtils;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.inventory.StorageAccessWrapper;
import com.tiviacz.travelersbackpack.inventory.upgrades.pickup.AutoPickupUpgrade;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public class AutoPickupTBBridge {
    public static ItemStack insertIntoTB(Player player, ItemStack stack) {
        BackpackWrapper wrapper = AttachmentUtils.getBackpackWrapper(player);
        if (wrapper == null) return stack;

        var upgradeOpt = wrapper.getUpgradeManager().getUpgrade(AutoPickupUpgrade.class);
        if (upgradeOpt.isPresent() && upgradeOpt.get().canPickup(stack)) {
            StorageAccessWrapper storage = wrapper.getStorageForInputOutput();
            ItemResource resource = ItemResource.of(stack);

            try (Transaction tx = Transaction.openRoot()) {
                int remaining = stack.getCount();
                for (int i = 0; i < storage.size(); i++) {
                    remaining -= storage.insert(i, resource, remaining, tx);
                    if (remaining <= 0) break;
                }
                if (remaining < stack.getCount()) {
                    tx.commit();
                    stack.setCount(remaining);
                }
            }
        }
        return stack;
    }
}
