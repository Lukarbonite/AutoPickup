package com.lukehinojosa.autopickup.mixin.compat; // Adjust to your package structure

import com.lukehinojosa.autopickup.AutoPickup;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World; // For one of the host method parameters
import org.jetbrains.annotations.Nullable; // Standard nullability, good practice
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
// import org.spongepowered.asm.mixin.injection.Coerce; // May not be needed if types match well

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "de.miraculixx.veinminer.VeinMinerEvent", remap = false)
public abstract class VeinMinerEventMixin {

    /**
     * Redirects the call to Block.getDrops within VeinMiner's improvedDropResources method.
     * We intercept the list of drops, attempt to auto-pickup them, and return only
     * the items that couldn't be picked up. VeinMiner then proceeds with its logic
     * (including its custom spawnAfterBreak for XP) using this modified list.
     *
     * Host Method (VeinMinerEvent.improvedDropResources - Kotlin):
     * private fun improvedDropResources(
     *     blockState: BlockState,       // idir_blockStateParam
     *     world: Level,                 // idir_worldParam (maps to net.minecraft.world.World)
     *     blockPos: BlockPos,           // idir_blockPosParam
     *     blockEntity: BlockEntity?,    // idir_blockEntityParam
     *     breaker: Entity,              // idir_breakerParam
     *     tool: ItemStack,              // idir_toolParam
     *     initialSource: BlockPos       // idir_initialSourceParam
     * )
     *
     * Target Method (Block.getDroppedStacks - from user's Block.java, which is Block.getDrops in Yarn mappings):
     * public static List<ItemStack> getDroppedStacks(
     *     BlockState state,             // stateForGetDrops
     *     ServerWorld world,           // worldForGetDrops
     *     BlockPos pos,               // posForGetDrops
     *     @Nullable BlockEntity blockEntity, // blockEntityForGetDrops
     *     @Nullable Entity entity,          // entityForGetDrops (this is the 'breaker' VeinMiner passes)
     *     ItemStack stack                // stackForGetDrops (this is the 'tool' VeinMiner passes)
     * )
     */
    @Redirect(
            method = "improvedDropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/Block;getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;")
            // Note: The target string uses getDroppedStacks, matching the user's provided Block.java.
            // If Yarn mappings name it getDrops, that might need adjustment if the raw bytecode uses getDrops.
            // Assuming getDroppedStacks is the correct final name here.
    )
    private static List<ItemStack> autopickup_redirectGetDropsForVeinminer(
            // Parameters for the TARGET method Block.getDroppedStacks:
            BlockState stateForGetDrops, ServerWorld worldForGetDrops, BlockPos posForGetDrops,
            @Nullable BlockEntity blockEntityForGetDrops, @Nullable Entity entityForGetDrops, ItemStack stackForGetDrops,

            // Original parameters of the HOST method (improvedDropResources), captured by Mixin:
            // We only need some of these, primarily the 'breaker' (idir_breakerParam)
            // and the host 'world' (idir_worldParam) to check gamerules.
            BlockState idir_blockStateParam,       // blockState from improvedDropResources
            World      idir_worldParam,            // world from improvedDropResources (net.minecraft.world.World)
            BlockPos   idir_blockPosParam,         // blockPos from improvedDropResources
            @Nullable BlockEntity idir_blockEntityParam, // blockEntity from improvedDropResources (use @Nullable)
            Entity     idir_breakerParam,          // breaker from improvedDropResources
            ItemStack  idir_toolParam,             // tool from improvedDropResources
            BlockPos   idir_initialSourceParam     // initialSource from improvedDropResources
    ) {

        // Call the original Block.getDroppedStacks to get the list VeinMiner would have processed
        List<ItemStack> originalDrops = Block.getDroppedStacks(stateForGetDrops, worldForGetDrops, posForGetDrops, blockEntityForGetDrops, entityForGetDrops, stackForGetDrops);

        // Ensure we are on a server world (idir_worldParam should be a ServerWorld in this context)
        // and the gamerule is on.
        if (!(idir_worldParam instanceof ServerWorld currentServerWorld)) {
            return originalDrops; // Should not happen if VeinMiner's improvedDropResources is server-side
        }
        if (!currentServerWorld.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)) {
            return originalDrops; // Auto-pickup gamerule is off
        }

        // The actual player who broke the block is idir_breakerParam
        if (idir_breakerParam instanceof PlayerEntity player) {
            if (player.isSpectator()) {
                return originalDrops; // Spectators shouldn't pick things up
            }

            List<ItemStack> remainingDrops = new ArrayList<>();
            boolean loggedThisVeinMine = false;

            for (ItemStack dropStack : originalDrops) {
                if (dropStack == null || dropStack.isEmpty()) continue;

                if (!player.getInventory().insertStack(dropStack.copy())) {
                    remainingDrops.add(dropStack); // Keep for VeinMiner to drop normally
                } else {
                    // Optionally log successful pickup
                    if (!loggedThisVeinMine) {
                        AutoPickup.LOGGER.info("[AutoPickupCompat] VeinMiner items being auto-picked up by {}", player.getName().getString());
                        loggedThisVeinMine = true;
                    }
                }
            }
            // VeinMiner will now iterate over 'remainingDrops'.
            // If all items were picked up, this list is empty, and VeinMiner's popResource loop does nothing for items.
            // VeinMiner's subsequent call to its "spawnAfterBreak" will still run, handling XP etc.
            return remainingDrops;
        }

        // If the breaker isn't a PlayerEntity, return original drops for VeinMiner to handle.
        return originalDrops;
    }
}