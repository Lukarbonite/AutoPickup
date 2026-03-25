//package com.lukarbonite.autopickup.mixin.compat.veinminer;
//
//import com.lukarbonite.autopickup.AutoPickupApi;
//import com.lukarbonite.autopickup.AutoPickupSessions;
//import de.miraculixx.veinminer.VeinMinerEvent;
//import net.minecraft.world.level.block.Block;
//import net.minecraft.world.level.block.state.BlockState;
//import net.minecraft.world.level.block.Blocks;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.item.ItemStack;
//import net.minecraft.server.level.ServerLevel;
//import net.minecraft.core.BlockPos;
//import net.minecraft.world.level.Level;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//
//import java.util.List;
//
//@Mixin(value = VeinMinerEvent.class, remap = false)
//public abstract class VeinMinerEventMixin {
//
//    // TODO(Ravel): target method destroyBlock with the signature not found
//// TODO(Ravel): target method destroyBlock with the signature not found
//// TODO(Ravel): target method destroyBlock with the signature not found
//// TODO(Ravel): target method destroyBlock with the signature not found
//// TODO(Ravel): target method destroyBlock with the signature not found
//// TODO(Ravel): target method destroyBlock with the signature not found
///**
//     * Injects into Veinminer's private destroyBlock method.
//     * This intercepts the block destruction to handle drops via AutoPickup.
//     */
//    @Inject(
//            method = "destroyBlock(Lnet/minecraft/class_2680;Lnet/minecraft/class_1799;Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_1657;Lnet/minecraft/class_2338;)V",
//            at = @At("HEAD"),
//            cancellable = true
//    )
//    private void autopickup_hijackVeinminerBlockDestroy(
//            BlockState blockState,
//            ItemStack tool,
//            Level world,
//            BlockPos position,
//            Player player,
//            BlockPos initialSource,
//            CallbackInfo ci
//    ) {
//        if (world.isClientSide() || !(world instanceof ServerLevel serverWorld)) {
//            return;
//        }
//
//        // Logic from VeinMinerEvent: Only process drops if the block is not air
//        // and (no tool is required OR the tool is suitable).
//        if (blockState.getBlock() != Blocks.AIR && (!blockState.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(blockState))) {
//
//            // Set the player context immediately for the experience mixin.
//            AutoPickupApi.setBlockBreaker(player);
//
//            // Register this block break with the session tracker.
//            // This ensures that when onStacksDropped -> dropExperience is called,
//            // BlockDropExperienceMixin can correctly identify the player owner
//            // even if this block is far from the player entity.
//            AutoPickupSessions.addBreak(player, position);
//
//            try {
//                // Calculate drops.
//                List<ItemStack> drops = Block.getDrops(blockState, serverWorld, position, world.getBlockEntity(position), player, tool);
//
//                // Check Config logic handled inside tryPickup
//                List<ItemStack> remainingItems = AutoPickupApi.tryPickup(player, drops);
//
//                for (ItemStack stack : remainingItems) {
//                    player.drop(stack, true);
//                }
//
//                // Manually call onStacksDropped to trigger the experience drop.
//                blockState.spawnAfterBreak(serverWorld, position, tool, true);
//
//                // Play the break sound/particles (LevelEvent 2001).
//                world.levelEvent(2001, position, Block.getId(blockState));
//            } finally {
//                // Always clear the context.
//                AutoPickupApi.clearBlockBreaker();
//            }
//        }
//
//        // Replicate logic: destroy the block.
//        // VeinMinerEvent uses world.method_8650(position, false) which maps to breakBlock(pos, drop).
//        // Passing false ensures vanilla drops are skipped (since we handled them),
//        // but block removal and updates occur correctly.
//        world.destroyBlock(position, false);
//
//        // We have completely taken over the method, so cancel the original.
//        ci.cancel();
//    }
//}