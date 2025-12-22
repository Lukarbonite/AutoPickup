package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
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

        AutoPickupSessions.begin(player);
        AutoPickupSessions.addBreak(player, pos);
        AutoPickupSessions.beginDropContext(player, pos);
        try {
            List<ItemStack> drops = Block.getDroppedStacks(state, serverWorld, pos, blockEntity, entity, tool);

            // tryPickup now handles the config checks (Master & Blocks) internally.
            // If disabled, it simply returns the original list.
            List<ItemStack> remainingDrops = AutoPickupApi.tryPickup(player, drops);

            for (ItemStack stack : remainingDrops) {
                Block.dropStack(world, pos, stack);
            }

            state.onStacksDropped(serverWorld, pos, tool, true);

            ci.cancel();
        } finally {
            AutoPickupSessions.endDropContext(player);
        }
    }
}