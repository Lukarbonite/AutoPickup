package com.lukarbonite.autopickup.mixin.compat.treeharvester;

import com.lukarbonite.autopickup.AutoPickupConfig;
import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.natamus.treeharvester_common_fabric.data.Variables;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import oshi.util.tuples.Triplet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Mixin for Tree Harvester's leaf decay logic.
 * Targets: com.natamus.treeharvester_common_fabric.events.LeafEvents
 */
@Mixin(targets = "com.natamus.treeharvester_common_fabric.events.LeafEvents")
public abstract class LeafEventsMixin {

    // Target for Production / Standard Environment
    @Redirect(
            method = "onWorldTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/natamus/collective_common_fabric/functions/BlockFunctions;dropBlock"
            ),
            remap = false
    )
    private static void autopickup_hijackLeafDrop(World world, BlockPos pos) {
        performHijack(world, pos);
    }

    @Unique
    private static void performHijack(World world, BlockPos pos) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        // Attempt to find the player who likely chopped the tree
        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        PlayerEntity player = AutoPickupSessions.findOwner(center);

        AutoPickupConfig config = AutoPickupConfig.getInstance();
        boolean shouldPickup = player != null
                && config.autoPickup
                && config.autoPickupBlocks;

        if (shouldPickup) {
            BlockState state = world.getBlockState(pos);
            AutoPickupApi.setBlockBreaker(player);

            // Refresh session to keep the "connection" alive as leaves decay
            AutoPickupSessions.addBreak(player, pos);

            try {
                // Leaves usually don't require a tool, passing empty stack or main hand is fine
                ItemStack tool = player.getMainHandStack();
                List<ItemStack> drops = Block.getDroppedStacks(state, serverWorld, pos, world.getBlockEntity(pos), player, tool);

                // Separate saplings from other drops to handle replanting logic safely
                List<ItemStack> saplings = new ArrayList<>();
                Iterator<ItemStack> it = drops.iterator();
                while (it.hasNext()) {
                    ItemStack stack = it.next();
                    if (isSapling(stack)) {
                        saplings.add(stack);
                        it.remove();
                    }
                }

                // 1. Pickup non-sapling drops (sticks, apples, etc)
                List<ItemStack> remaining = AutoPickupApi.tryPickup(player, drops);
                for (ItemStack stack : remaining) {
                    Block.dropStack(world, pos, stack);
                }

                // 2. Handle Saplings
                boolean plantedOne = false;
                boolean needsReplant = shouldForceDropSaplings(pos);

                for (ItemStack saplingStack : saplings) {
                    // If we need to replant and haven't dropped one for TreeHarvester yet...
                    if (needsReplant && !plantedOne && !saplingStack.isEmpty()) {
                        ItemStack one = saplingStack.split(1);
                        spawnDelayedItem(world, pos, one);
                        plantedOne = true;
                    }

                    // Pickup whatever is left in this stack
                    if (!saplingStack.isEmpty()) {
                        List<ItemStack> remSap = AutoPickupApi.tryPickup(player, Collections.singletonList(saplingStack));
                        for (ItemStack s : remSap) {
                            Block.dropStack(world, pos, s);
                        }
                    }
                }

                state.onStacksDropped(serverWorld, pos, tool, true);

                // Play break sound/particles
                world.syncWorldEvent(2001, pos, Block.getRawIdFromState(state));

                // Break block without drops
                world.breakBlock(pos, false);
            } finally {
                AutoPickupApi.clearBlockBreaker();
            }
        } else {
            // Default behavior if no player is found nearby
            world.breakBlock(pos, true);
        }
    }

    @Unique
    private static boolean shouldForceDropSaplings(BlockPos pos) {
        try {
            if (Variables.saplingPositions == null || Variables.saplingPositions.isEmpty()) {
                return false;
            }
            for (Object obj : Variables.saplingPositions) {
                if (obj instanceof Triplet) {
                    Triplet<?, ?, ?> triplet = (Triplet<?, ?, ?>) obj;
                    Object b = triplet.getB();
                    if (b instanceof BlockPos) {
                        if (((BlockPos) b).isWithinDistance(pos, 32)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable t) {
        }
        return false;
    }

    @Unique
    private static void spawnDelayedItem(World world, BlockPos pos, ItemStack stack) {
        if (!stack.isEmpty() && world instanceof ServerWorld) {
            double d = (world.random.nextFloat() * 0.5F) + 0.25D;
            double e = (world.random.nextFloat() * 0.5F) + 0.25D;
            double f = (world.random.nextFloat() * 0.5F) + 0.25D;

            ItemEntity entity = new ItemEntity(world, pos.getX() + d, pos.getY() + e, pos.getZ() + f, stack);
            entity.setToDefaultPickupDelay();
            entity.addCommandTag("autopickup_delayed");
            world.spawnEntity(entity);
        }
    }

    @Unique
    private static boolean isSapling(ItemStack stack) {
        return stack.isIn(ItemTags.SAPLINGS) || (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock().getDefaultState().isIn(BlockTags.SAPLINGS));
    }
}