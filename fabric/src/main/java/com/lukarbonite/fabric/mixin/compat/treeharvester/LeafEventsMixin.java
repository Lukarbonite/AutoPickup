package com.lukarbonite.fabric.mixin.compat.treeharvester;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.lukarbonite.autopickup.util.OwnedBlockPos;
import com.natamus.treeharvester_common_fabric.events.LeafEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.CopyOnWriteArrayList;

@Mixin(value = LeafEvents.class, remap = false)
public class LeafEventsMixin {
    @WrapOperation(method = "onWorldTick", at = @At(value = "INVOKE", target = "Lcom/natamus/collective_common_fabric/functions/BlockFunctions;dropBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"), remap = true)
    private static void wrapDrop(Level level, BlockPos pos, Operation<Void> original) {
        handle(level, pos, () -> original.call(level, pos));
    }

    @WrapOperation(method = "onWorldTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"), remap = true)
    private static void wrapRandom(BlockState instance, ServerLevel world, BlockPos pos, RandomSource random, Operation<Void> original) {
        handle(world, pos, () -> original.call(instance, world, pos, random));
    }

    @WrapOperation(
            method = "onWorldTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V"),
            remap = true
    )
    private static void wrapTick(BlockState instance, ServerLevel world, BlockPos pos,
                                 RandomSource random, Operation<Void> original) {
        handle(world, pos, () -> original.call(instance, world, pos, random));
    }

    @WrapOperation(method = "onNeighbourNotify", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CopyOnWriteArrayList;add(Ljava/lang/Object;)Z"), remap = false)
    private static boolean tagTickLeaf(CopyOnWriteArrayList instance, Object element, Operation<Boolean> original) {
        if (element instanceof BlockPos pos && AutoPickupApi.getBlockBreaker() != null) {
            return original.call(instance, new OwnedBlockPos(pos, AutoPickupApi.getBlockBreaker().getUUID()));
        }
        return original.call(instance, element);
    }

    private static void handle(Level world, BlockPos pos, Runnable action) {
        if (pos instanceof OwnedBlockPos owned && world.getServer() != null) {
            Player p = world.getServer().getPlayerList().getPlayer(owned.getOwnerUUID());
            if (p != null) {
                AutoPickupSessions.begin(p);
                AutoPickupSessions.addBreak(p, pos);

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