package com.lukarbonite.forge.mixin.compat.travelersbackpack;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.forge.compat.travelersbackpack.AutoPickupTBBridge;
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

        // Pass a copy as to not mutate the world instance if the transaction rolls back
        ItemStack remainder = insertIntoTB(player, stack.copy());

        stack.setCount(remainder.getCount());
        if (stack.isEmpty())
            return true;

        return inventory.add(stack);
    }

    @Unique
    private static ItemStack insertIntoTB(Player player, ItemStack stack) {
        try {
            return AutoPickupTBBridge.insertIntoTB(player, stack);
        } catch (NoClassDefFoundError | Exception e) {
            return stack;
        }
    }
}