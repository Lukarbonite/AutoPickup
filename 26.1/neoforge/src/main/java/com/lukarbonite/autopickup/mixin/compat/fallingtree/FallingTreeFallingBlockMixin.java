package com.lukarbonite.autopickup.mixin.compat.fallingtree;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.lukarbonite.autopickup.compat.fallingtree.FallingTreeLeafTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockEntity.class)
public class FallingTreeFallingBlockMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (self.level().isClientSide()) return;
        Player player = AutoPickupSessions.peekFallingBlockOwner(self.getId());
        if (player == null) {
            player = AutoPickupSessions.findLinkedOwnerForBreak(self.blockPosition());
            if (player != null) {
                AutoPickupSessions.trackFallingBlock(self.getId(), player);
            }
        }
        if (player == null) return;
        AutoPickupSessions.begin(player);
        AutoPickupSessions.beginDropContext(player, self.blockPosition());
        TICK_CONTEXT_PLAYER.set(player);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (self.level().isClientSide()) return;
        Player player = TICK_CONTEXT_PLAYER.get();
        TICK_CONTEXT_PLAYER.remove();
        if (player == null) return;
        AutoPickupSessions.endDropContext(player);
        if (!self.isAlive()) {
            AutoPickupSessions.getAndRemoveFallingBlockOwner(self.getId());
        }
    }

    private static final ThreadLocal<Player> TICK_CONTEXT_PLAYER = new ThreadLocal<>();

    @WrapOperation(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z")
    )
    private boolean wrapLandingSetBlock(Level level, BlockPos pos, BlockState state, int flags,
                                         Operation<Boolean> original) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        boolean placed = original.call(level, pos, state, flags);
        if (placed && !level.isClientSide()) {
            Player player = AutoPickupSessions.getAndRemoveFallingBlockOwner(self.getId());
            if (player != null && state.getBlock() instanceof LeavesBlock) {
                FallingTreeLeafTracker.trackPos(pos, player.getUUID());
            }
        }
        return placed;
    }
}
