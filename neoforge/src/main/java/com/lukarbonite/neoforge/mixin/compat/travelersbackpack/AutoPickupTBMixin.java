package com.lukarbonite.neoforge.mixin.compat.travelersbackpack;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.tiviacz.travelersbackpack.capability.AttachmentUtils;
import com.tiviacz.travelersbackpack.inventory.BackpackWrapper;
import com.tiviacz.travelersbackpack.inventory.upgrades.pickup.AutoPickupUpgrade;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AutoPickupApi.class)
public class AutoPickupTBMixin {

    @Redirect(method = "insertDrops",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"),
            remap = false)
    private static boolean interceptAdd_tryPickup(Inventory inventory, ItemStack stack) {
        return tryInsert(inventory, stack);
    }

    @Unique
    private static boolean tryInsert(Inventory inventory, ItemStack stack) {
        Player player = inventory.player;
        ItemStack remainder = insertIntoTB(player, stack.copy());
        stack.setCount(remainder.getCount());
        if (stack.isEmpty()) return true;
        return inventory.add(stack);
    }

    @Unique
    private static ItemStack insertIntoTB(Player player, ItemStack stack) {
        BackpackWrapper wrapper = AttachmentUtils.getBackpackWrapper(player);
        if (wrapper == null) return stack;

        var upgradeOpt = wrapper.getUpgradeManager().getUpgrade(AutoPickupUpgrade.class);
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
