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

    @Inject(method = "useItemOn", at = @At("TAIL"))
    private void autopickup_closeUseContext(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        AutoPickupSessions.closeUseContext();
    }

    private static void registerColumn(ServerPlayer player, Level level, BlockPos clicked, Block block) {
        BlockPos scan = clicked.below();
        while (level.getBlockState(scan).is(block)) {
            AutoPickupSessions.addBreak(player, scan);
            scan = scan.below();
        }
        scan = clicked.above();
        while (level.getBlockState(scan).is(block)) {
            AutoPickupSessions.addBreak(player, scan);
            scan = scan.above();
        }
    }
}
