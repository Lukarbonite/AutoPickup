package com.lukarbonite.autopickup.mixin.compat.travelersbackpack;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.compat.travelersbackpack.AutoPickupTBBridge;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AutoPickupApi.class)
public class AutoPickupTBMixin {

    @WrapOperation(
            method = "insertDrops",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z")
    )
    private static boolean interceptInventoryAdd(Inventory inventory, ItemStack stack, Operation<Boolean> original) {
        Player player = inventory.player;

        ItemStack remainder = insertIntoTB(player, stack.copy());
        stack.setCount(remainder.getCount());

        if (stack.isEmpty()) {
            return true;
        }

        return original.call(inventory, stack);
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
