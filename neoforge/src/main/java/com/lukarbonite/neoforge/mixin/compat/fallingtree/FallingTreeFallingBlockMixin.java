package com.lukarbonite.neoforge.mixin.compat.fallingtree;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.lukarbonite.neoforge.compat.fallingtree.FallingTreeLeafTracker;
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

    /**
     * Each tick, if this entity is owned by a player (tracked by ServerLevelMixin when
     * FallingTree calls addFreshEntity), open a tight drop context at the entity's current
     * position. This ensures that when tick() eventually calls spawnAtLocation() because
     * the block cannot be placed ("bounce"), the spawned ItemEntity is attributed to that
     * player and auto-picked up.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        // This mixin must only run on the logical server. In single-player the entity also
        // ticks on the client thread; running there would race on the shared DROP_GUARDS deque.
        if (self.level().isClientSide()) return;
        Player player = AutoPickupSessions.peekFallingBlockOwner(self.getId());
        if (player == null) {
            // On Fabric, FallingTree may defer addFreshEntity past the end of breakTree,
            // so blockBreaker is already cleared when the entity is added and tracking was skipped.
            // Fall back to the active session: all tree-part positions were registered in onHead,
            // so findLinkedOwnerForBreak matches the entity's starting block position precisely.
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

    /**
     * Close the drop context opened in onTickHead.
     * If the entity was discarded this tick (placed or bounced), also remove it from tracking.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (self.level().isClientSide()) return;
        Player player = TICK_CONTEXT_PLAYER.get();
        TICK_CONTEXT_PLAYER.remove();
        if (player == null) return;
        AutoPickupSessions.endDropContext(player);
        if (!self.isAlive()) {
            // Entity was discarded this tick — clean up tracking entry if still present.
            AutoPickupSessions.getAndRemoveFallingBlockOwner(self.getId());
        }
    }

    // ThreadLocal so that onTickHead and onTickTail share the player reference safely
    // across a single synchronous tick on the server thread.
    private static final ThreadLocal<Player> TICK_CONTEXT_PLAYER = new ThreadLocal<>();

    /**
     * When the falling block successfully re-places itself (leaves landing), remove the
     * entity from tracking and forward the landing position to FallingTreeLeafTracker so
     * any subsequent decay drops are attributed to the correct player.
     */
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
