package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Links blocks broken through {@code World.destroyBlock} (vein miners, cascades) to the player whose
 * action caused them, scoped to the break call. Same role as LevelBreakBlockMixin on newer versions;
 * 1.12.2's destroyBlock has no breaker argument, so every dropping call goes through the strict finder.
 */
@Mixin(World.class)
public abstract class WorldMixin {

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void autopickup_openLinkedContext(BlockPos pos, boolean dropBlock, CallbackInfoReturnable<Boolean> cir) {
        World self = (World) (Object) this;
        if (!dropBlock || self.isRemote) return;
        // Read at HEAD, while the block is still present, for the finder's same-type column check.
        Block block = self.getBlockState(pos).getBlock();
        boolean supportBelowGone = self.isAirBlock(pos.down());
        AutoPickupSessions.openLinkedDropContextStrict(pos, block, supportBelowGone);
    }

    // RETURN with the HEAD condition: destroyBlock returns early for air, and every HEAD push above
    // must be popped exactly once.
    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void autopickup_closeLinkedContext(BlockPos pos, boolean dropBlock, CallbackInfoReturnable<Boolean> cir) {
        World self = (World) (Object) this;
        if (!dropBlock || self.isRemote) return;
        AutoPickupSessions.closeLinkedDropContext();
    }
}
