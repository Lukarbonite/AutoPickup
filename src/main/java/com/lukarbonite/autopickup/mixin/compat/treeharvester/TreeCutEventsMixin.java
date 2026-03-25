//package com.lukarbonite.autopickup.mixin.compat.treeharvester;
//
//import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//import com.lukarbonite.autopickup.AutoPickupApi;
//import com.lukarbonite.autopickup.AutoPickupSessions;
//import com.natamus.treeharvester_common_fabric.config.ConfigHandler;
//import com.natamus.treeharvester_common_fabric.events.TreeCutEvents;
//import com.natamus.treeharvester_common_fabric.util.Util;
//import net.minecraft.world.level.block.Block;
//import net.minecraft.world.level.block.state.BlockState;
//import net.minecraft.world.level.block.entity.BlockEntity;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.item.ItemStack;
//import net.minecraft.core.registries.BuiltInRegistries;
//import net.minecraft.core.BlockPos;
//import net.minecraft.world.level.Level;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.Unique;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//
//@Mixin(value = TreeCutEvents.class, remap = false)
//public class TreeCutEventsMixin {
//
//    @Unique
//    private static final ThreadLocal<BlockPos> capturedBottomPos = new ThreadLocal<>();
//
//    @Inject(method = "onTreeHarvest", at = @At("HEAD"))
//    private static void onHead(Level level, Player player, BlockPos bpos, BlockState state, BlockEntity be, CallbackInfoReturnable<Boolean> cir) {
//        AutoPickupApi.setBlockBreaker(player);
//    }
//
//    /**
//     * CAPTURE THE BOTTOM: TreeHarvester updates its local bpos variable
//     * just before calling isTreeAndReturnLogAmount. We capture it here.
//     */
//    @WrapOperation(
//            method = "onTreeHarvest",
//            at = @At(value = "INVOKE", target = "Lcom/natamus/treeharvester_common_fabric/processing/TreeProcessing;isTreeAndReturnLogAmount(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I")
//    )
//    private static int captureRealBottom(Level level, BlockPos pos, Operation<Integer> original) {
//        capturedBottomPos.set(pos.immutable());
//        return original.call(level, pos);
//    }
//
//    @Inject(method = "onTreeHarvest", at = @At("RETURN"))
//    private static void onReturn(Level level, Player player, BlockPos bpos, BlockState state, BlockEntity be, CallbackInfoReturnable<Boolean> cir) {
//        BlockPos bottom = capturedBottomPos.get();
//        capturedBottomPos.remove(); // Clean up memory
//
//        if (ConfigHandler.replaceSaplingOnTreeHarvest && bottom != null) {
//            // Only replant if the block is now empty (broken)
//            if (level.getBlockState(bottom).isAir()) {
//                Block logBlock = state.getBlock();
//                String logId = BuiltInRegistries.BLOCK.getKey(logBlock).getPath();
//                // Extract wood type (e.g. "birch", "dark_oak")
//                String woodType = logId.replace("_log", "").replace("_stem", "").replace("_wood", "").replace("_hyphae", "");
//
//                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
//                    ItemStack stack = player.getInventory().getItem(i);
//                    Block potentialSapling = Block.byItem(stack.getItem());
//
//                    if (!stack.isEmpty() && Util.isSapling(potentialSapling)) {
//                        String saplingId = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
//
//                        // Exact Species Match
//                        if (saplingId.contains(woodType)) {
//                            level.setBlockAndUpdate(bottom, potentialSapling.defaultBlockState());
//                            if (!player.isCreative()) stack.shrink(1);
//                            break;
//                        }
//                    }
//                }
//            }
//        }
//        AutoPickupApi.clearBlockBreaker();
//    }
//
//    @WrapOperation(
//            method = "onTreeHarvest",
//            at = @At(value = "INVOKE", target = "Lcom/natamus/collective_common_fabric/functions/BlockFunctions;dropBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V")
//    )
//    private static void wrapLogDrop(Level world, BlockPos pos, Operation<Void> original) {
//        Player player = AutoPickupApi.getBlockBreaker();
//        if (player != null) {
//            AutoPickupSessions.beginDropContext(player, pos);
//            original.call(world, pos);
//            AutoPickupSessions.endDropContext(player);
//        } else {
//            original.call(world, pos);
//        }
//    }
//}