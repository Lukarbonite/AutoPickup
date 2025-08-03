package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickup;
import com.lukarbonite.autopickup.AutoPickupApi;
import de.miraculixx.veinminer.Veinminer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

@Mixin(value = Veinminer.class, remap = false)
public abstract class VeinMinerEventMixin {

    // Shadow the private damageItem method so we can call it from our overwritten logic.
    @Shadow
    private void damageItem(ItemStack item, PlayerEntity player) {
        throw new AssertionError("Mixin shadow method called unexpectedly.");
    }

    /**
     * @author Luke & Gemini
     * @reason The original 'breakAdjusted' method is private and uses recursion, which prevents a clean Mixin injection.
     * This @Overwrite replaces the entire method with an iterative (non-recursive) algorithm. This new implementation
     * searches for blocks in the vein while seamlessly integrating the AutoPickup API for each block,
     * resolving all compilation and compatibility issues.
     */
    @Overwrite
    private int breakAdjusted(BlockState source, String target, ItemStack item, int delay, int max, Set<BlockPos> processedBlocks, World world, BlockPos position, PlayerEntity player) {
        if (world.isClient() || !world.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)) {
            // Let the original player's break action handle the first block, but stop the chain reaction.
            return 0;
        }

        ServerWorld serverWorld = (ServerWorld) world;
        String sourceKey = source.getBlock().getTranslationKey();

        // Initial check for the very first block
        if (!sourceKey.equals(target) || processedBlocks.contains(position)) {
            return 0;
        }

        // Use a Queue for an iterative Breadth-First Search.
        Queue<BlockPos> blocksToBreak = new LinkedList<>();
        blocksToBreak.add(position);

        int blocksBrokenCount = 0;

        while (!blocksToBreak.isEmpty() && processedBlocks.size() < max) {
            if (item.isEmpty() || item.getDamage() >= item.getMaxDamage()) {
                break; // Stop if the tool is about to break.
            }

            BlockPos currentPos = blocksToBreak.poll();
            if (processedBlocks.contains(currentPos)) {
                continue; // Skip if already processed in an earlier part of the search
            }

            BlockState currentState = world.getBlockState(currentPos);
            if (!currentState.getBlock().getTranslationKey().equals(target)) {
                continue;
            }

            processedBlocks.add(currentPos);
            blocksBrokenCount++;

            // Damage item for every block after the first one.
            if (blocksBrokenCount > 1) {
                this.damageItem(item, player);
            }

            // --- Auto-Pickup Logic ---
            AutoPickupApi.setBlockBreaker(player);
            try {
                List<ItemStack> drops = Block.getDroppedStacks(currentState, serverWorld, currentPos, world.getBlockEntity(currentPos), player, item);
                List<ItemStack> remainingItems = AutoPickupApi.tryPickup(player, drops);

                for (ItemStack stack : remainingItems) {
                    Block.dropStack(world, currentPos, stack);
                }
                currentState.onStacksDropped(serverWorld, currentPos, item, true);
            } finally {
                AutoPickupApi.clearBlockBreaker();
            }

            // Destroy the block
            world.setBlockState(currentPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            world.syncWorldEvent(2001, currentPos, Block.getRawIdFromState(currentState));

            // Search for neighbors and add them to the queue
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        blocksToBreak.add(currentPos.add(x, y, z));
                    }
                }
            }
        }

        return blocksBrokenCount;
    }
}