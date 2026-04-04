package com.lukarbonite.autopickup.mixin.compat.treeharvester;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.natamus.treeharvester_common_neoforge.events.TreeCutEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TreeCutEvents.class, remap = false)
public class TreeCutEventsMixin {

    @Inject(method = "onTreeHarvest", at = @At("HEAD"))
    private static void onHead(Level level, Player player, BlockPos bpos, BlockState state, BlockEntity be, CallbackInfoReturnable<Boolean> cir) {
        AutoPickupApi.setBlockBreaker(player);
    }

    @Inject(method = "onTreeHarvest", at = @At("RETURN"))
    private static void onReturn(Level level, Player player, BlockPos bpos, BlockState state, BlockEntity be, CallbackInfoReturnable<Boolean> cir) {
        AutoPickupApi.clearBlockBreaker();
    }

    @WrapOperation(
            method = "onTreeHarvest",
            at = @At(value = "INVOKE", target = "Lcom/natamus/collective_common_neoforge/functions/BlockFunctions;dropBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V")
    )
    private static void wrapLogDrop(Level world, BlockPos pos, Operation<Void> original) {
        Player player = AutoPickupApi.getBlockBreaker();
        if (player != null) {
            AutoPickupSessions.beginDropContext(player, pos);
            original.call(world, pos);
            AutoPickupSessions.endDropContext(player);
        } else {
            original.call(world, pos);
        }
    }
}