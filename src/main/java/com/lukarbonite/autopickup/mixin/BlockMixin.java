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

    @Inject(method = "dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true)
    private static void autopickup_onDropStacks(BlockState state, World world, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemStack tool, CallbackInfo ci) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        // If the gamerule is off, do nothing and let vanilla's logic run.
        if (!serverWorld.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)) {
            return;
        }

        if (entity instanceof PlayerEntity player) {
            // Set the player context for the experience-capturing mixin.
            AutoPickupApi.setBlockBreaker(player);
            try {
                // Calculate the drops as vanilla would.
                List<ItemStack> drops = Block.getDroppedStacks(state, serverWorld, pos, blockEntity, entity, tool);

                // Use our own API to attempt item pickup.
                List<ItemStack> remainingDrops = AutoPickupApi.tryPickup(player, drops);

                // Drop any items that couldn't be picked up.
                for (ItemStack stack : remainingDrops) {
                    player.dropItem(stack, true);
                }

                // This vanilla method triggers experience drop logic for blocks like ores.
                // Our BlockDropExperienceMixin will intercept the call to dropExperience within it.
                state.onStacksDropped(serverWorld, pos, tool, true);

            } finally {
                // Always clear the context afterwards.
                AutoPickupApi.clearBlockBreaker();
            }

            // We have handled all drop logic, so cancel the original method.
            ci.cancel();
        }
    }
}