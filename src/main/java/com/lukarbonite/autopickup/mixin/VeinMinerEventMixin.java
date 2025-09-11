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
            BlockState blockState,
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

        // Set the player context immediately for the experience mixin.
        AutoPickupApi.setBlockBreaker(player);
        try {
            // Calculate drops.
            List<ItemStack> drops = Block.getDroppedStacks(blockState, serverWorld, position, world.getBlockEntity(position), player, tool);

            // Check if item pickup is enabled.
            boolean shouldPickupItems = serverWorld.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)
                    && serverWorld.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_BLOCKS_GAMERULE_KEY);

            if (shouldPickupItems) {
                List<ItemStack> remainingItems = AutoPickupApi.tryPickup(player, drops);
                for (ItemStack stack : remainingItems) {
                    player.dropItem(stack, true);
                }
            } else {
                for (ItemStack stack : drops) {
                    player.dropItem(stack, true);
                }
            }

            // Manually call onStacksDropped to trigger the experience drop.
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