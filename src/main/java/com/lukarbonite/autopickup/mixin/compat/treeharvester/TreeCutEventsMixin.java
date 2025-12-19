package com.lukarbonite.autopickup.mixin.compat.treeharvester;

import com.lukarbonite.autopickup.AutoPickup;
import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin for Tree Harvester's main cutting logic.
 * Targets: com.natamus.treeharvester_common_fabric.events.TreeCutEvents
 */
@Mixin(targets = "com.natamus.treeharvester_common_fabric.events.TreeCutEvents")
public abstract class TreeCutEventsMixin {

    // ThreadLocal to pass the player from the main method to the Redirect
    private static final ThreadLocal<PlayerEntity> HARVESTING_PLAYER = new ThreadLocal<>();

    /**
     * Capture the player entity at the start of the harvest event.
     */
    @Inject(
            method = "onTreeHarvest(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/entity/BlockEntity;)Z",
            at = @At("HEAD")
    )
    private static void autopickup_capturePlayer(World level, PlayerEntity player, BlockPos bpos, BlockState state, BlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        HARVESTING_PLAYER.set(player);
    }

    /**
     * Clear the player entity reference when the method finishes.
     */
    @Inject(
            method = "onTreeHarvest(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/entity/BlockEntity;)Z",
            at = @At("RETURN")
    )
    private static void autopickup_releasePlayer(World level, PlayerEntity player, BlockPos bpos, BlockState state, BlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        HARVESTING_PLAYER.remove();
    }

    /**
     * Redirects the call to Collective's BlockFunctions.dropBlock.
     * This allows us to intercept the block break, calculate drops, and give them to the player via AutoPickup.
     */
    @Redirect(
            method = "onTreeHarvest(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/entity/BlockEntity;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/natamus/collective_common_fabric/functions/BlockFunctions;dropBlock(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V"
            )
    )
    private static void autopickup_hijackDropBlock(World world, BlockPos pos) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        PlayerEntity player = HARVESTING_PLAYER.get();
        BlockState state = world.getBlockState(pos);

        // Standard AutoPickup checks
        boolean shouldPickup = player != null
                && serverWorld.getGameRules().getValue(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)
                && serverWorld.getGameRules().getValue(AutoPickup.AUTO_PICKUP_BLOCKS_GAMERULE_KEY);

        if (player != null && shouldPickup) {
            // Register this position to the session so Experience Mixins (if valid) can find the owner
            AutoPickupSessions.addBreak(player, pos);
            AutoPickupApi.setBlockBreaker(player);

            try {
                // Get the drops as if the player broke it with their current hand item
                ItemStack tool = player.getMainHandStack();
                List<ItemStack> drops = Block.getDroppedStacks(state, serverWorld, pos, world.getBlockEntity(pos), player, tool);

                // Attempt pickup
                List<ItemStack> remaining = AutoPickupApi.tryPickup(player, drops);
                for (ItemStack stack : remaining) {
                    Block.dropStack(world, pos, stack);
                }

                // Trigger experience drop logic
                state.onStacksDropped(serverWorld, pos, tool, true);

                // Play break sound/particles
                world.syncWorldEvent(2001, pos, Block.getRawIdFromState(state));

                // Break the block without dropping standard loot (passed false)
                world.breakBlock(pos, false);
            } finally {
                AutoPickupApi.clearBlockBreaker();
            }
        } else {
            // Fallback: Default behavior (Break block and drop items normally)
            world.breakBlock(pos, true);
        }
    }
}