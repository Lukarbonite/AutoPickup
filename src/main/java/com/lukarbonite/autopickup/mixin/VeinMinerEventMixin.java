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
     * Injects into Veinminer's private destroyBlock method, which is called for every
     * subsequent block in a vein. This gives us full control over the drop and experience logic.
     */
    @Inject(
            method = "destroyBlock(Lnet/minecraft/class_2680;Lnet/minecraft/class_1799;Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_1657;Lnet/minecraft/class_2338;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void autopickup_hijackVeinminerBlockDestroy(
            BlockState blockState, // This is a mapped name, which is fine for the method body
            ItemStack tool,
            World world,
            BlockPos position,
            PlayerEntity player,
            BlockPos initialSource,
            CallbackInfo ci
    ) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        // If the gamerule is off, let VeinMiner handle drops normally by not cancelling.
        if (!serverWorld.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)) {
            return;
        }

        // Set the player context so our other mixins can identify the block breaker.
        AutoPickupApi.setBlockBreaker(player);
        try {
            // Get drops before the block is destroyed.
            List<ItemStack> drops = Block.getDroppedStacks(blockState, serverWorld, position, world.getBlockEntity(position), player, tool);

            // Process drops with our API.
            List<ItemStack> remainingItems = AutoPickupApi.tryPickup(player, drops);

            // Drop any items that were not picked up.
            for (ItemStack stack : remainingItems) {
                player.dropItem(stack, true);
            }

            // This vanilla method contains the call to dropExperience, which our BlockDropExperienceMixin will intercept.
            // This is called AFTER Veinminer applies durability damage but BEFORE we destroy the block.
            blockState.onStacksDropped(serverWorld, position, tool, true);
        } finally {
            // Always clear the context.
            AutoPickupApi.clearBlockBreaker();
        }

        // Replicate the final logic from Veinminer's method: play effect and destroy block.
        world.syncWorldEvent(2001, position, Block.getRawIdFromState(blockState));
        world.setBlockState(position, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);

        // We have completely taken over, so cancel the original method.
        ci.cancel();
    }
}