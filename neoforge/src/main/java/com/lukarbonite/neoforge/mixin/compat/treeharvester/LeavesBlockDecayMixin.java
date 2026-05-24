package com.lukarbonite.neoforge.mixin.compat.treeharvester;

import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts vanilla leaf decay (randomTick) so that items dropped by leaves
 * not in TreeHarvester's processing queue — outer leaves on large trees that
 * lose log support naturally — can still be attributed to the player who cut
 * the tree. Opens a linked drop context for the duration of the decay tick;
 * if any nearby session position (accumulated as TreeHarvester processes inner
 * leaves) is within LINK_RADIUS, the drops are captured.
 */
@Mixin(LeavesBlock.class)
public class LeavesBlockDecayMixin {

    private static boolean autopickup_isDecaying(BlockState state) {
        // MAX_DISTANCE == 7; a leaf at distance 7 with no log support decays.
        return !state.getValue(LeavesBlock.PERSISTENT)
                && state.getValue(LeavesBlock.DISTANCE) >= 7;
    }

    @Inject(method = "randomTick", at = @At("HEAD"))
    private void autopickup_openDecayContext(BlockState state, ServerLevel level, BlockPos pos,
                                             RandomSource random, CallbackInfo ci) {
        if (autopickup_isDecaying(state)) {
            AutoPickupSessions.openLinkedDropContext(pos);
        }
    }

    @Inject(method = "randomTick", at = @At("TAIL"))
    private void autopickup_closeDecayContext(BlockState state, ServerLevel level, BlockPos pos,
                                              RandomSource random, CallbackInfo ci) {
        if (autopickup_isDecaying(state)) {
            AutoPickupSessions.closeLinkedDropContext();
        }
    }
}
