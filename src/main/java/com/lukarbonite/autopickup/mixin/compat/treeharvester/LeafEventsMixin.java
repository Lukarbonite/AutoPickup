package com.lukarbonite.autopickup.mixin.compat.treeharvester;

import com.lukarbonite.autopickup.AutoPickup;
import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Mixin for Tree Harvester's leaf decay logic.
 * Targets: com.natamus.treeharvester_common_fabric.events.LeafEvents
 */
@Mixin(targets = "com.natamus.treeharvester_common_fabric.events.LeafEvents")
public abstract class LeafEventsMixin {

    /**
     * Redirects the call to Collective's BlockFunctions.dropBlock inside onWorldTick.
     * Since leaves break on a server tick without a direct player reference,
     * we use AutoPickupSessions to find the closest active player.
     */
    @Redirect(
            method = "onWorldTick(Lnet/minecraft/server/world/ServerWorld;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/natamus/collective_common_fabric/functions/BlockFunctions;dropBlock(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V"
            )
    )
    private static void autopickup_hijackLeafDrop(World world, BlockPos pos) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        // Attempt to find the player who likely chopped the tree
        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        PlayerEntity player = AutoPickupSessions.findOwner(center);

        boolean shouldPickup = player != null
                && serverWorld.getGameRules().getValue(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)
                && serverWorld.getGameRules().getValue(AutoPickup.AUTO_PICKUP_BLOCKS_GAMERULE_KEY);

        if (shouldPickup) {
            BlockState state = world.getBlockState(pos);
            AutoPickupApi.setBlockBreaker(player);

            // Refresh session to keep the "connection" alive as leaves decay
            AutoPickupSessions.addBreak(player, pos);

            try {
                // Leaves usually don't require a tool, passing empty stack or main hand is fine
                ItemStack tool = player.getMainHandStack();
                List<ItemStack> drops = Block.getDroppedStacks(state, serverWorld, pos, world.getBlockEntity(pos), player, tool);

                List<ItemStack> remaining = AutoPickupApi.tryPickup(player, drops);
                for (ItemStack stack : remaining) {
                    Block.dropStack(world, pos, stack);
                }

                // Experience (rare for leaves, but possible)
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
}