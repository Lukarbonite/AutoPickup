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

        // If a drop context is already open (normal break via ServerPlayerGameMode),
        // let dropResources run normally so NeoForge's XP pipeline is not cut off.
        // ServerLevelMixin intercepts the item spawns via the active context.
        if (AutoPickupSessions.hasDropContext(player)) {
            return;
        }

        // No context yet: this is a direct dropResources call from a mod (e.g. LiteMiner).
        // Handle manually and cancel to prevent double-spawning.
        AutoPickupSessions.begin(player);
        AutoPickupSessions.addBreak(player, pos);
        AutoPickupSessions.beginDropContext(player, pos);
        try {
            List<ItemStack> drops = Block.getDrops(state, serverWorld, pos, blockEntity, entity, tool);
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