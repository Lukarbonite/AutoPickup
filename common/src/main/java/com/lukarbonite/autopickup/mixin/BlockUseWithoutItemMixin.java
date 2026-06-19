package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class BlockUseWithoutItemMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void autopickup_openUseContext(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        BlockPos pos = hitResult.getBlockPos();
        AutoPickupSessions.openUseContext(player, pos);

        if (level.getBlockState(pos).is(Blocks.SUGAR_CANE)) {
            registerColumn(player, level, pos, Blocks.SUGAR_CANE);
        } else if (level.getBlockState(pos).is(Blocks.CACTUS)) {
            registerColumn(player, level, pos, Blocks.CACTUS);
        }
    }

    // RETURN (not TAIL): useItemOn has several early-return paths (e.g. when the clicked
    // block's interaction returns a non-PASS result, as a lever/button does). TAIL only
    // injects before the final return, so early returns would leak the use context — leaving
    // a multi-block pickup zone alive for the session's lifetime. That leaked guard is what
    // let redstone-driven items (a dropper dispensing on its scheduled tick, piston-farm
    // drops, etc.) get attributed to the player. Closing on every return scopes the context
    // to the synchronous interaction only: genuine harvest drops (sweet berries, jukebox
    // eject, sugar-cane/cactus columns) still spawn in-call and are picked up, while anything
    // a functional block ejects asynchronously falls outside the window. No block list needed.
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void autopickup_closeUseContext(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        AutoPickupSessions.closeUseContext();
    }

    private static void registerColumn(ServerPlayer player, Level level, BlockPos clicked, Block block) {
        BlockPos scan = clicked.below();
        while (level.getBlockState(scan).is(block)) {
            AutoPickupSessions.addUsePos(player, scan);
            scan = scan.below();
        }
        scan = clicked.above();
        while (level.getBlockState(scan).is(block)) {
            AutoPickupSessions.addUsePos(player, scan);
            scan = scan.above();
        }
    }
}
