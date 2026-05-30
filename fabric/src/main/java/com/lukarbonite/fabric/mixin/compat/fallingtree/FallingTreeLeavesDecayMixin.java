package com.lukarbonite.fabric.mixin.compat.fallingtree;

import com.lukarbonite.autopickup.AutoPickupSessions;
import com.lukarbonite.fabric.compat.fallingtree.FallingTreeLeafTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(LeavesBlock.class)
public class FallingTreeLeavesDecayMixin {

    // Records the pos for which we opened a context this tick so TAIL can balance it.
    private static final ThreadLocal<BlockPos> OPENED_FOR = ThreadLocal.withInitial(() -> null);

    @Inject(method = "randomTick", at = @At("HEAD"))
    private void autopickup_openFallingTreeContext(BlockState state, ServerLevel level,
                                                   BlockPos pos, RandomSource random, CallbackInfo ci) {
        UUID uuid = FallingTreeLeafTracker.findOwner(pos);
        if (uuid == null) return;
        Player player = level.getPlayerByUUID(uuid);
        if (player == null) return;
        AutoPickupSessions.begin(player);
        AutoPickupSessions.addBreak(player, pos);
        AutoPickupSessions.openLinkedDropContext(pos);
        OPENED_FOR.set(pos.immutable());
    }

    @Inject(method = "randomTick", at = @At("TAIL"))
    private void autopickup_closeFallingTreeContext(BlockState state, ServerLevel level,
                                                    BlockPos pos, RandomSource random, CallbackInfo ci) {
        BlockPos opened = OPENED_FOR.get();
        if (opened == null || !opened.equals(pos)) return;
        OPENED_FOR.remove();
        AutoPickupSessions.closeLinkedDropContext();
    }
}
