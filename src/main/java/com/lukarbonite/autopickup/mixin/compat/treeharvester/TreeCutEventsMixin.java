package com.lukarbonite.autopickup.mixin.compat.treeharvester;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.natamus.treeharvester_common_fabric.config.ConfigHandler;
import com.natamus.treeharvester_common_fabric.events.TreeCutEvents;
import com.natamus.treeharvester_common_fabric.util.Util;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TreeCutEvents.class, remap = false)
public class TreeCutEventsMixin {

    @Unique
    private static final ThreadLocal<BlockPos> capturedBottomPos = new ThreadLocal<>();

    @Inject(method = "onTreeHarvest", at = @At("HEAD"))
    private static void onHead(World level, PlayerEntity player, BlockPos bpos, BlockState state, BlockEntity be, CallbackInfoReturnable<Boolean> cir) {
        AutoPickupApi.setBlockBreaker(player);
    }

    /**
     * CAPTURE THE BOTTOM: TreeHarvester updates its local bpos variable
     * just before calling isTreeAndReturnLogAmount. We capture it here.
     */
    @WrapOperation(
            method = "onTreeHarvest",
            at = @At(value = "INVOKE", target = "Lcom/natamus/treeharvester_common_fabric/processing/TreeProcessing;isTreeAndReturnLogAmount(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)I")
    )
    private static int captureRealBottom(World level, BlockPos pos, Operation<Integer> original) {
        capturedBottomPos.set(pos.toImmutable());
        return original.call(level, pos);
    }

    @Inject(method = "onTreeHarvest", at = @At("RETURN"))
    private static void onReturn(World level, PlayerEntity player, BlockPos bpos, BlockState state, BlockEntity be, CallbackInfoReturnable<Boolean> cir) {
        BlockPos bottom = capturedBottomPos.get();
        capturedBottomPos.remove(); // Clean up memory

        if (ConfigHandler.replaceSaplingOnTreeHarvest && bottom != null) {
            // Only replant if the block is now empty (broken)
            if (level.getBlockState(bottom).isAir()) {
                Block logBlock = state.getBlock();
                String logId = Registries.BLOCK.getId(logBlock).getPath();
                // Extract wood type (e.g. "birch", "dark_oak")
                String woodType = logId.replace("_log", "").replace("_stem", "").replace("_wood", "").replace("_hyphae", "");

                for (int i = 0; i < player.getInventory().size(); i++) {
                    ItemStack stack = player.getInventory().getStack(i);
                    Block potentialSapling = Block.getBlockFromItem(stack.getItem());

                    if (!stack.isEmpty() && Util.isSapling(potentialSapling)) {
                        String saplingId = Registries.ITEM.getId(stack.getItem()).getPath();

                        // Exact Species Match
                        if (saplingId.contains(woodType)) {
                            level.setBlockState(bottom, potentialSapling.getDefaultState());
                            if (!player.isCreative()) stack.decrement(1);
                            break;
                        }
                    }
                }
            }
        }
        AutoPickupApi.clearBlockBreaker();
    }

    @WrapOperation(
            method = "onTreeHarvest",
            at = @At(value = "INVOKE", target = "Lcom/natamus/collective_common_fabric/functions/BlockFunctions;dropBlock(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V")
    )
    private static void wrapLogDrop(World world, BlockPos pos, Operation<Void> original) {
        PlayerEntity player = AutoPickupApi.getBlockBreaker();
        if (player != null) {
            AutoPickupSessions.beginDropContext(player, pos);
            original.call(world, pos);
            AutoPickupSessions.endDropContext(player);
        } else {
            original.call(world, pos);
        }
    }
}