package com.lukarbonite.fabric.mixin.compat.fallingtree;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.lukarbonite.fabric.compat.fallingtree.FallingTreeLeafTracker;
import fr.rakambda.fallingtree.common.leaf.LeafBreakingHandler;
import fr.rakambda.fallingtree.common.leaf.LeafBreakingSchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LeafBreakingHandler.class, remap = false)
public class LeafBreakingHandlerMixin {

    @Inject(method = "addSchedule", at = @At("HEAD"), remap = false)
    private void onAddSchedule(LeafBreakingSchedule schedule, CallbackInfo ci) {
        BlockPos pos = ((BlockPos) schedule.getBlockPos().getRaw()).immutable();
        Player player = AutoPickupApi.getBlockBreaker();
        if (player == null) {
            // During leaf decay cascades blockBreaker is no longer set.
            // Fall back to the active session to propagate tracking through each wave.
            player = AutoPickupSessions.findLinkedOwnerForBreak(pos);
        }
        if (player == null) return;
        FallingTreeLeafTracker.trackPos(pos, player.getUUID());
    }
}
