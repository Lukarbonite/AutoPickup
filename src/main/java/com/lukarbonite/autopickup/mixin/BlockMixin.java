package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickup;
import com.lukarbonite.autopickup.AutoPickupApi;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Block.class)
public abstract class BlockMixin {

    @Inject(
            method = "dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void autopickup_onDropStacks(BlockState state, World world, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemStack tool, CallbackInfo ci) {
        if (!(world instanceof ServerWorld serverWorld) || !(entity instanceof PlayerEntity player)) {
            return;
        }

        // Set the player context immediately. This is crucial for the experience mixin.
        AutoPickupApi.setBlockBreaker(player);
        try {
            // Calculate drops as vanilla would.
            List<ItemStack> drops = Block.getDroppedStacks(state, serverWorld, pos, blockEntity, entity, tool);

            // Check if item pickup is enabled.
            boolean shouldPickupItems = serverWorld.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)
                    && serverWorld.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_BLOCKS_GAMERULE_KEY);

            if (shouldPickupItems) {
                // Use our API to attempt item pickup.
                List<ItemStack> remainingDrops = AutoPickupApi.tryPickup(player, drops);
                // Drop any items that couldn't be picked up.
                for (ItemStack stack : remainingDrops) {
                    Block.dropStack(world, pos, stack);
                }
            } else {
                // If item pickup is disabled, drop all items normally.
                for (ItemStack stack : drops) {
                    Block.dropStack(world, pos, stack);
                }
            }

            // Manually call onStacksDropped. This is what triggers the dropExperience call.
            // Our BlockDropExperienceMixin will intercept it, and it will now work because
            // the block breaker context is correctly set.
            state.onStacksDropped(serverWorld, pos, tool, true);

        } finally {
            // Always clear the context afterwards.
            AutoPickupApi.clearBlockBreaker();
        }

        // We have handled all drop logic (items and experience), so cancel the original method.
        ci.cancel();
    }
}