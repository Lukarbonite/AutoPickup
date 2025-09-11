package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickup;
import com.lukarbonite.autopickup.AutoPickupApi;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {

    /**
     * Injects into the spawnEntity method to intercept item drops.
     * This is used for compatibility with mods like Liteminer that spawn
     * their item drops directly without using Block.dropStacks.
     */
    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void autopickup_interceptItemSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        // Check if the entity is an ItemEntity and if a block breaker is set by our compat handlers.
        if (entity instanceof ItemEntity itemEntity) {
            PlayerEntity player = AutoPickupApi.getBlockBreaker();
            ServerWorld world = (ServerWorld) (Object) this;

            if (player != null && !player.isSpectator() && world.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)) {
                ItemStack stackToPickup = itemEntity.getStack();

                // Use the API to attempt to pick up the item.
                List<ItemStack> remainingItems = AutoPickupApi.tryPickup(player, Collections.singletonList(stackToPickup));

                // If the list is empty, it means the entire stack was picked up.
                if (remainingItems.isEmpty()) {
                    // Cancel the entity spawn completely.
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }

                // If some items remain (e.g., inventory full), update the entity's stack.
                // The original spawnEntity method will then proceed with this smaller stack.
                itemEntity.setStack(remainingItems.get(0));
            }
        }
    }
}