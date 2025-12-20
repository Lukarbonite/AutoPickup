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
     * This is used for compatibility with mods like Tree Harvester that spawn
     * their item drops directly without using Block.dropStacks.
     */
    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void autopickup_interceptItemSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ItemEntity itemEntity) {

            // Allow "delayed" items to spawn so other mods (Tree Harvester) can react to them first.
            if (entity.getCommandTags().contains("autopickup_delayed")) {
                return;
            }

            ServerWorld world = (ServerWorld) (Object) this;

            // Skip items with an owner: player hand-drops, entity-produced items (e.g., chicken eggs)
            Entity ownerEntity = itemEntity.getOwner();
            if (ownerEntity != null) return;

            // Only intercept if we are within an active player drop-context
            net.minecraft.util.math.Vec3d spawnPos = new net.minecraft.util.math.Vec3d(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
            PlayerEntity owner = com.lukarbonite.autopickup.AutoPickupSessions.findOwnerInDropContext(spawnPos);

            // Same-tick tiny-radius fallback if no active drop context matched (for mods that spawn ItemEntity directly)
            if (owner == null) {
                owner = com.lukarbonite.autopickup.AutoPickupSessions.findOwnerSameTickTight(spawnPos);
            }

            if (owner != null
                    && !owner.isSpectator()
                    && world.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)
                    && world.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_BLOCKS_GAMERULE_KEY)) {
                ItemStack stackToPickup = itemEntity.getStack();

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
                itemEntity.setStack(remainingItems.getFirst());
            }
        }
    }

    @Inject(method = "spawnEntity", at = @At("TAIL"))
    private void autopickup_cleanupDelayedSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        // If we allowed an item to spawn via the "delayed" tag, try to pick it up now
        // that other mods have had their chance to react to ENTITY_LOAD.
        if (entity instanceof ItemEntity itemEntity && entity.getCommandTags().contains("autopickup_delayed")) {
            // If the item is dead or empty, another mod (Tree Harvester) consumed it.
            if (!itemEntity.isAlive() || itemEntity.getStack().isEmpty()) {
                return;
            }

            ServerWorld world = (ServerWorld) (Object) this;
            net.minecraft.util.math.Vec3d spawnPos = new net.minecraft.util.math.Vec3d(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());

            // Find owner again. Usually session context is still valid.
            PlayerEntity owner = com.lukarbonite.autopickup.AutoPickupSessions.findOwnerInDropContext(spawnPos);
            if (owner == null) {
                owner = com.lukarbonite.autopickup.AutoPickupSessions.findOwnerSameTickTight(spawnPos);
            }
            if (owner == null) {
                owner = com.lukarbonite.autopickup.AutoPickupSessions.findOwner(spawnPos);
            }

            if (owner != null
                    && !owner.isSpectator()
                    && world.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)
                    && world.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_BLOCKS_GAMERULE_KEY)) {

                ItemStack stackToPickup = itemEntity.getStack();
                List<ItemStack> remainingItems = AutoPickupApi.tryPickup(owner, Collections.singletonList(stackToPickup));

                if (remainingItems.isEmpty()) {
                    itemEntity.discard();
                } else {
                    itemEntity.setStack(remainingItems.getFirst());
                    itemEntity.removeCommandTag("autopickup_delayed");
                }
            }
        }
    }
}