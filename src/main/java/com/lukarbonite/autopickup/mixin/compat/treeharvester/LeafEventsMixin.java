package com.lukarbonite.autopickup.mixin.compat.treeharvester;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.lukarbonite.autopickup.util.OwnedBlockPos;
import com.natamus.treeharvester_common_fabric.events.LeafEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = LeafEvents.class, remap = false)
public class LeafEventsMixin {
    @WrapOperation(method = "onWorldTick", at = @At(value = "INVOKE", target = "Lcom/natamus/collective_common_fabric/functions/BlockFunctions;dropBlock(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V"))
    private static void wrapDrop(World level, BlockPos pos, Operation<Void> original) {
        handle(level, pos, () -> original.call(level, pos));
    }

    @WrapOperation(method = "onWorldTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;randomTick(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/random/Random;)V", remap = true))
    private static void wrapRandom(BlockState instance, ServerWorld world, BlockPos pos, Random random, Operation<Void> original) {
        handle(world, pos, () -> original.call(instance, world, pos, random));
    }

    private static void handle(World world, BlockPos pos, Runnable action) {
        if (pos instanceof OwnedBlockPos owned && world.getServer() != null) {
            PlayerEntity p = world.getServer().getPlayerManager().getPlayer(owned.getOwnerUUID());
            if (p != null) {

                // This keeps the player's session alive in AutoPickupSessions.
                // As long as leaves are falling, the session will not time out.
                AutoPickupSessions.begin(p);

                AutoPickupApi.setBlockBreaker(p);
                AutoPickupSessions.beginDropContext(p, pos);

                action.run();

                AutoPickupSessions.endDropContext(p);
                AutoPickupApi.clearBlockBreaker();
                return;
            }
        }
        action.run();
    }
}