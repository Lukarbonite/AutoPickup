package com.lukarbonite.fabric.mixin.compat.pandafallingtrees;

import com.lukarbonite.autopickup.AutoPickupSessions;
import dev.pandasystems.fallingtrees.entity.TreeEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TreeEntity.class, remap = false)
public class TreeEntityMixin {

    // ThreadLocal so HEAD and RETURN share the same player reference on the server thread.
    private static final ThreadLocal<Player> DROPPING_PLAYER = new ThreadLocal<>();

    /**
     * Before TreeEntity drops its pre-computed item list, open a drop context at its current
     * position so ServerLevelMixin can attribute every spawned ItemEntity to the owner.
     */
    @Inject(method = "dropItems", at = @At("HEAD"), remap = false)
    private void onDropItemsHead(CallbackInfo ci) {
        TreeEntity self = (TreeEntity) (Object) this;
        Entity ownerEntity = self.owner;
        if (!(ownerEntity instanceof Player player)) return;
        BlockPos pos = self.blockPosition();
        AutoPickupSessions.begin(player);
        AutoPickupSessions.beginDropContext(player, pos);
        DROPPING_PLAYER.set(player);
    }

    @Inject(method = "dropItems", at = @At("RETURN"), remap = false)
    private void onDropItemsReturn(CallbackInfo ci) {
        Player player = DROPPING_PLAYER.get();
        DROPPING_PLAYER.remove();
        if (player != null) {
            AutoPickupSessions.endDropContext(player);
        }
    }
}
