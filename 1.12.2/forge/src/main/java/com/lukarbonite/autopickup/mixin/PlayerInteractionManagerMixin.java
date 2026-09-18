package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.12.2's ServerPlayerGameMode: {@code tryHarvestBlock} is the player's own block break, and
 * {@code processRightClickBlock} a right-click on a block. Same role as ServerPlayerGameModeMixin
 * and BlockUseWithoutItemMixin on newer versions.
 */
@Mixin(PlayerInteractionManager.class)
public abstract class PlayerInteractionManagerMixin {

    @Shadow public EntityPlayerMP player;

    @Inject(method = "tryHarvestBlock", at = @At("HEAD"))
    private void autopickup_onTryHarvestBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // Mark that we are on this player's break stack for the whole call, so synchronous non-player
        // breaks/drops it causes (vein mining, cactus/sugar-cane cascades) are attributed while
        // independent world-tick farms are not. Balanced by exitPlayerBreak at RETURN.
        AutoPickupSessions.enterPlayerBreak(this.player);
        AutoPickupSessions.begin(this.player);
        AutoPickupSessions.addBreak(this.player, pos);
    }

    @Inject(method = "tryHarvestBlock", at = @At("RETURN"))
    private void autopickup_onTryHarvestBlockReturn(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        AutoPickupSessions.exitPlayerBreak();
    }

    @Inject(method = "processRightClickBlock", at = @At("HEAD"))
    private void autopickup_openUseContext(EntityPlayer player, World world, ItemStack stack, EnumHand hand, BlockPos pos,
                                           EnumFacing facing, float hitX, float hitY, float hitZ,
                                           CallbackInfoReturnable<EnumActionResult> cir) {
        AutoPickupSessions.openUseContext(player, pos);

        Block clicked = world.getBlockState(pos).getBlock();
        if (clicked == Blocks.REEDS || clicked == Blocks.CACTUS) {
            registerColumn(player, world, pos, clicked);
        }
    }

    // RETURN, not TAIL: processRightClickBlock has several early returns, and leaking the use
    // context would leave a pickup zone open for the session's lifetime (see BlockUseWithoutItemMixin).
    @Inject(method = "processRightClickBlock", at = @At("RETURN"))
    private void autopickup_closeUseContext(EntityPlayer player, World world, ItemStack stack, EnumHand hand, BlockPos pos,
                                            EnumFacing facing, float hitX, float hitY, float hitZ,
                                            CallbackInfoReturnable<EnumActionResult> cir) {
        AutoPickupSessions.closeUseContext();
    }

    private static void registerColumn(EntityPlayer player, World world, BlockPos clicked, Block block) {
        BlockPos scan = clicked.down();
        while (world.getBlockState(scan).getBlock() == block) {
            AutoPickupSessions.addUsePos(player, scan);
            scan = scan.down();
        }
        scan = clicked.up();
        while (world.getBlockState(scan).getBlock() == block) {
            AutoPickupSessions.addUsePos(player, scan);
            scan = scan.up();
        }
    }
}
