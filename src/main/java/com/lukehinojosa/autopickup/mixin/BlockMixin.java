package com.lukehinojosa.autopickup.mixin; // Or your actual mixin package

import com.lukehinojosa.autopickup.AutoPickup;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld; // Still needed for world.getGameRules() and Block.getDroppedStacks if it requires ServerWorld
import net.minecraft.world.World; // Changed here
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Block.class)
public abstract class BlockMixin {

    // Updated signature: Lnet/minecraft/world/World; instead of Lnet/minecraft/server/world/ServerWorld;
    @Inject(method = "dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true)
    private static void autopickup_onDropStacks(BlockState state, World world, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemStack tool, CallbackInfo ci) {
        // Check if we are on the server side and the world is a ServerWorld for game rules and specific drop calculation
        if (!(world instanceof ServerWorld serverWorld)) { // Perform instanceof check and cast
            return; // Not a ServerWorld, so let vanilla handle it (e.g., client side block breaking effects)
        }

        // Now use serverWorld for operations requiring it
        if (!serverWorld.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)) {
            return;
        }

        if (entity instanceof PlayerEntity player) {
            if (player.isSpectator()) {
                return;
            }

            // Block.getDroppedStacks often expects a ServerWorld
            List<ItemStack> drops = Block.getDroppedStacks(state, serverWorld, pos, blockEntity, entity, tool);

            if (drops.isEmpty()) {
                ci.cancel();
                return;
            }

            for (ItemStack itemStack : drops) {
                if (!itemStack.isEmpty()) {
                    if (!player.getInventory().insertStack(itemStack.copy())) {
                        Block.dropStack(serverWorld, pos, itemStack); // Use serverWorld here too for consistency
                    }
                }
            }
            ci.cancel();
        }
    }
}