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
import org.spongepowered.asm.mixin.Unique;
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

    @Unique
    private static final ThreadLocal<PlayerEntity> HARVESTING_PLAYER = new ThreadLocal<>();

    // --- CAPTURE PLAYER ---

    @Inject(
            method = "onTreeHarvest(Lnet/minecraft/class_1937;Lnet/minecraft/class_1657;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_2586;)Z",
            at = @At("HEAD"),
            remap = true,
            require = 0
    )
    private static void capturePlayer_Intermediary(World level, PlayerEntity player, BlockPos bpos, BlockState state, BlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        HARVESTING_PLAYER.set(player);
    }

    @Inject(
            method = "onTreeHarvest(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/entity/BlockEntity;)Z",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void capturePlayer_Named(World level, PlayerEntity player, BlockPos bpos, BlockState state, BlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        HARVESTING_PLAYER.set(player);
    }

    // --- RELEASE PLAYER ---

    @Inject(
            method = "onTreeHarvest(Lnet/minecraft/class_1937;Lnet/minecraft/class_1657;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_2586;)Z",
            at = @At("RETURN"),
            remap = true,
            require = 0
    )
    private static void releasePlayer_Intermediary(World level, PlayerEntity player, BlockPos bpos, BlockState state, BlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        HARVESTING_PLAYER.remove();
    }

    @Inject(
            method = "onTreeHarvest(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/entity/BlockEntity;)Z",
            at = @At("RETURN"),
            remap = false,
            require = 0
    )
    private static void releasePlayer_Named(World level, PlayerEntity player, BlockPos bpos, BlockState state, BlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        HARVESTING_PLAYER.remove();
    }

    // --- HIJACK DROPS ---

    @Redirect(
            method = "onTreeHarvest(Lnet/minecraft/class_1937;Lnet/minecraft/class_1657;Lnet/minecraft/class_2338;Lnet/minecraft/class_2680;Lnet/minecraft/class_2586;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/natamus/collective_common_fabric/functions/BlockFunctions;dropBlock(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;)V"
            ),
            remap = true,
            require = 0
    )
    private static void hijackDrop_Intermediary(World world, BlockPos pos) {
        performHijack(world, pos);
    }

    @Redirect(
            method = "onTreeHarvest(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/entity/BlockEntity;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/natamus/collective_common_fabric/functions/BlockFunctions;dropBlock(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V"
            ),
            remap = false,
            require = 0
    )
    private static void hijackDrop_Named(World world, BlockPos pos) {
        performHijack(world, pos);
    }

    @Unique
    private static void performHijack(World world, BlockPos pos) {
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