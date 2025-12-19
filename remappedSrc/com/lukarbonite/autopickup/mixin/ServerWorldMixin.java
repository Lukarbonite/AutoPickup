package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickup;
import com.lukarbonite.autopickup.AutoPickupApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

@Mixin(ServerLevel.class)
public abstract class ServerWorldMixin {

    /**
     * Injects into the spawnEntity method to intercept item drops.
     * This is used for compatibility with mods like Liteminer that spawn
     * their item drops directly without using Block.dropStacks.
     */
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void autopickup_interceptItemSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ItemEntity itemEntity) {
            ServerLevel world = (ServerLevel) (Object) this;

            // Skip items with an owner: player hand-drops, entity-produced items (e.g., chicken eggs)
            Entity ownerEntity = itemEntity.getOwner();
            if (ownerEntity != null) {
                return;
            }

            // Only intercept if we are within an active player drop-context
            net.minecraft.world.phys.Vec3 spawnPos = new net.minecraft.world.phys.Vec3(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
            Player owner = com.lukarbonite.autopickup.AutoPickupSessions.findOwnerInDropContext(spawnPos);

            // Same-tick tiny-radius fallback if no active drop context matched (for mods that spawn ItemEntity directly)
            if (owner == null) {
                owner = com.lukarbonite.autopickup.AutoPickupSessions.findOwnerSameTickTight(spawnPos);
            }

            if (owner != null
                    && !owner.isSpectator()
                    && world.getGameRules().get(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)
                    && world.getGameRules().get(AutoPickup.AUTO_PICKUP_BLOCKS_GAMERULE_KEY)) {
                ItemStack stackToPickup = itemEntity.getItem();

                // Use the API to attempt to pick up the item.
                List<ItemStack> remainingItems = AutoPickupApi.tryPickup(owner, Collections.singletonList(stackToPickup));

                // If the list is empty, it means the entire stack was picked up.
                if (remainingItems.isEmpty()) {
                    // Cancel the entity spawn completely.
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }

                // If some items remain (e.g., inventory full), update the entity's stack.
                // The original spawnEntity method will then proceed with this smaller stack.
                itemEntity.setItem(remainingItems.get(0));
            }
        }
    }
}