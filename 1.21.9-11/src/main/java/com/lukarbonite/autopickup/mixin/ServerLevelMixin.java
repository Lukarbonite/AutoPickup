package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    /**
     * Injects into the spawnEntity method to intercept item drops.
     * This is used for compatibility with mods like Tree Harvester that spawn
     * their item drops directly without using Block.dropStacks.
     */
    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void autopickup_interceptItemSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof FallingBlockEntity fbe) {
            Player breaker = AutoPickupApi.getBlockBreaker();
            if (breaker == null) {
                breaker = AutoPickupSessions.getAndRemovePendingFallOwner(fbe.blockPosition());
            }
            if (breaker != null) {
                AutoPickupSessions.trackFallingBlock(fbe.getId(), breaker);
            }
            return;
        }

        if (entity instanceof ItemEntity itemEntity) {

            // Allow "delayed" items to spawn so other mods (Tree Harvester) can react to them first.
            if (entity.getTags().contains("autopickup_delayed")) {
                return;
            }

            ServerLevel world = (ServerLevel) (Object) this;

            // Skip items with an owner: player hand-drops, entity-produced items (e.g., chicken eggs)
            Entity ownerEntity = itemEntity.getOwner();
            if (ownerEntity != null) return;

            // Only intercept if we are within an active player drop-context
            net.minecraft.world.phys.Vec3 spawnPos = new net.minecraft.world.phys.Vec3(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());
            Player owner = AutoPickupSessions.findOwnerInDropContext(spawnPos);

            // Same-tick tiny-radius fallback if no active drop context matched (for mods that spawn ItemEntity directly)
            if (owner == null) {
                owner = AutoPickupSessions.findOwnerSameTickTight(spawnPos);
            }

            // Session-radius fallback for mods that spread drops across multiple ticks (e.g. FallingTree FALL_ITEM mode).
            if (owner == null) {
                owner = AutoPickupSessions.findOwner(spawnPos);
            }

            // Wider fallback during active right-click use interactions (e.g. RightClickHarvest, sugarcane, cacti).
            if (owner == null) {
                owner = AutoPickupSessions.findOwnerInUseContext(spawnPos);
            }

            if (owner != null
                    && !owner.isSpectator()
                    && AutoPickupApi.isMasterEnabled(owner)
                    && AutoPickupApi.isBlocksEnabled(owner)) {
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
                itemEntity.setItem(remainingItems.getFirst());
            }
        }
    }

    @Inject(method = "addFreshEntity", at = @At("TAIL"))
    private void autopickup_cleanupDelayedSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        // If we allowed an item to spawn via the "delayed" tag, try to pick it up now
        // that other mods have had their chance to react to ENTITY_LOAD.
        if (entity instanceof ItemEntity itemEntity && entity.getTags().contains("autopickup_delayed")) {
            // If the item is dead or empty, another mod (Tree Harvester) consumed it.
            if (!itemEntity.isAlive() || itemEntity.getItem().isEmpty()) {
                return;
            }

            ServerLevel world = (ServerLevel) (Object) this;
            net.minecraft.world.phys.Vec3 spawnPos = new net.minecraft.world.phys.Vec3(itemEntity.getX(), itemEntity.getY(), itemEntity.getZ());

            Player owner = AutoPickupSessions.findOwnerInDropContext(spawnPos);
            if (owner == null) {
                owner = AutoPickupSessions.findOwnerSameTickTight(spawnPos);
            }
            if (owner == null) {
                owner = AutoPickupSessions.findOwner(spawnPos);
            }

            if (owner != null
                    && !owner.isSpectator()
                    && AutoPickupApi.isMasterEnabled(owner)
                    && AutoPickupApi.isBlocksEnabled(owner)) {

                ItemStack stackToPickup = itemEntity.getItem();
                List<ItemStack> remainingItems = AutoPickupApi.tryPickup(owner, Collections.singletonList(stackToPickup));

                if (remainingItems.isEmpty()) {
                    itemEntity.discard();
                } else {
                    itemEntity.setItem(remainingItems.getFirst());
                    itemEntity.removeTag("autopickup_delayed");
                }
            }
        }
    }
}