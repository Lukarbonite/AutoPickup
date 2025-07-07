package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickup;
import com.lukarbonite.autopickup.AutoPickupApi;
import de.miraculixx.veinminer.VeinMinerEvent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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

@Mixin(value = VeinMinerEvent.class, remap = false)
public abstract class VeinMinerEventMixin {

    /**
     * Injects into Veinminer's private destroyBlock method.
     * It runs BEFORE the original code, calculates the drops itself, processes them,
     * handles experience pickup, and then replaces the block with air. This causes
     * the original method to cede control of item and experience dropping to us.
     */
    @Inject(
            // This targets the private method using its Intermediary signature from the bytecode.
            method = "destroyBlock(Lnet/minecraft/class_2680;Lnet/minecraft/class_1799;Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_1657;Lnet/minecraft/class_2338;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void autopickup_hijackVeinminerBlockDestroy(
            BlockState blockState, // This is a yarn-mapped type
            ItemStack item,
            World world,
            BlockPos position,
            PlayerEntity player,
            BlockPos initialSource,
            CallbackInfo ci
    ) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        // If the gamerule is off, let VeinMiner handle drops normally.
        if (!serverWorld.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)) {
            return;
        }

        AutoPickupApi.setBlockBreaker(player);
        try {
            // Get drops before the block is destroyed
            List<ItemStack> drops = Block.getDroppedStacks(blockState, serverWorld, position, world.getBlockEntity(position), player, item);

            // Process drops with our API
            List<ItemStack> remainingItems = AutoPickupApi.tryPickup(player, drops);

            // Drop any items that were not picked up
            for (ItemStack stack : remainingItems) {
                player.dropItem(stack, true);
            }

            // This triggers experience drop logic, which our BlockDropExperienceMixin will intercept.
            blockState.onStacksDropped(serverWorld, position, item, true);
        } finally {
            AutoPickupApi.clearBlockBreaker();
        }


        // Play the block break effect
        world.syncWorldEvent(2001, position, Block.getRawIdFromState(blockState));

        // Now, we destroy the block by setting it to air and notifying neighbors.
        // This is the "bypass" part. The original method will now do nothing.
        world.setBlockState(position, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);

        // We have completely taken over, so cancel the original method.
        ci.cancel();
    }
}