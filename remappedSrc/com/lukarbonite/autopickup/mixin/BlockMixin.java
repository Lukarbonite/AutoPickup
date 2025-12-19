package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickup;
import com.lukarbonite.autopickup.AutoPickupApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(Block.class)
public abstract class BlockMixin {

    @Inject(
            method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void autopickup_onDropStacks(BlockState state, Level world, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemStack tool, CallbackInfo ci) {
        if (!(world instanceof ServerLevel serverWorld) || !(entity instanceof Player player)) {
            return;
        }

        // Begin or refresh per-player mining session and record this exact block position.
        com.lukarbonite.autopickup.AutoPickupSessions.begin(player);
        com.lukarbonite.autopickup.AutoPickupSessions.addBreak(player, pos);
        com.lukarbonite.autopickup.AutoPickupSessions.beginDropContext(player, pos);
        try {
            List<ItemStack> drops = Block.getDrops(state, serverWorld, pos, blockEntity, entity, tool);

            boolean shouldPickupItems = serverWorld.getGameRules().get(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)
                    && serverWorld.getGameRules().get(AutoPickup.AUTO_PICKUP_BLOCKS_GAMERULE_KEY);

            if (shouldPickupItems) {
                List<ItemStack> remainingDrops = AutoPickupApi.tryPickup(player, drops);
                for (ItemStack stack : remainingDrops) {
                    Block.popResource(world, pos, stack);
                }
            } else {
                for (ItemStack stack : drops) {
                    Block.popResource(world, pos, stack);
                }
            }

            state.spawnAfterBreak(serverWorld, pos, tool, true);

            ci.cancel();
        } finally {
            com.lukarbonite.autopickup.AutoPickupSessions.endDropContext(player);
        }
    }
}