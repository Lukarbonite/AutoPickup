package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// TODO(Ravel): can not resolve target class Block
// TODO(Ravel): can not resolve target class Block
// TODO(Ravel): can not resolve target class Block
// TODO(Ravel): can not resolve target class Block
@Mixin(Block.class)
public abstract class BlockMixin {

    // TODO(Ravel): no target class
// TODO(Ravel): no target class
// TODO(Ravel): no target class
// TODO(Ravel): no target class
    @Inject(
            method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void autopickup_onDropStacks(BlockState state, Level world, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemStack tool, CallbackInfo ci) {
        if (!(world instanceof ServerLevel serverWorld) || !(entity instanceof Player player)) {
            return;
        }

        AutoPickupSessions.begin(player);
        AutoPickupSessions.addBreak(player, pos);
        AutoPickupSessions.beginDropContext(player, pos);
        try {
            List<ItemStack> drops = Block.getDrops(state, serverWorld, pos, blockEntity, entity, tool);

            // tryPickup now handles the config checks (Master & Blocks) internally.
            // If disabled, it simply returns the original list.
            List<ItemStack> remainingDrops = AutoPickupApi.tryPickup(player, drops);

            for (ItemStack stack : remainingDrops) {
                Block.popResource(world, pos, stack);
            }

            state.spawnAfterBreak(serverWorld, pos, tool, true);

            ci.cancel();
        } finally {
            AutoPickupSessions.endDropContext(player);
        }
    }
}