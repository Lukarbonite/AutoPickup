package com.lukarbonite.autopickup.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends the tight per-player drop context to blocks broken by mods that bypass
 * Block.dropStacks with a PlayerEntity (e.g., liteminer). We link such breaks
 * to the nearest active mining session within a small radius and open a scoped drop context
 * only for the duration of the break call.
 */
@Mixin(Level.class)
public abstract class LevelBreakBlockMixin {

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void autopickup_openLinkedContext(BlockPos pos, boolean drop, Entity breaker, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        // Only consider cases where drops will be spawned and the breaker is NOT a player
        if (!drop || breaker instanceof Player) return;
        // Credit only genuine player-caused breaks: on-stack (synchronous vein/cascade during the
        // player's own action) or an off-stack same-type column segment collapsing onto a player-owned
        // break directly below it (bamboo/sugar-cane/cactus). The block and whether the support below is
        // already gone are read here (block still present at HEAD) for the finder to use.
        Level self = (Level) (Object) this;
        Block block = self.getBlockState(pos).getBlock();
        boolean supportBelowGone = self.getBlockState(pos.below()).isAir();
        com.lukarbonite.autopickup.AutoPickupSessions.openLinkedDropContextStrict(pos, block, supportBelowGone);
    }

    @Inject(method = "destroyBlock", at = @At("TAIL"))
    private void autopickup_closeLinkedContext(BlockPos pos, boolean drop, Entity breaker, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (!drop || breaker instanceof Player) return;
        // Close the context opened at HEAD (if any). This balances nested calls via a thread-local stack.
        com.lukarbonite.autopickup.AutoPickupSessions.closeLinkedDropContext();
    }
}
