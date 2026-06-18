package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brushing takes ~20 ticks to complete, so the useItemOn drop context is long
 * closed by the time the item spawns. Inject at dropContent to open a tight
 * context exactly when the brushed item is spawned.
 */
@Mixin(BrushableBlockEntity.class)
public class BrushableBlockMixin {

    @Inject(method = "dropContent", at = @At("HEAD"))
    private void autopickup_openBrushContext(ServerLevel level, LivingEntity brusher,
                                              ItemStack tool, CallbackInfo ci) {
        if (!(brusher instanceof Player player)) return;
        BrushableBlockEntity self = (BrushableBlockEntity) (Object) this;
        AutoPickupSessions.begin(player);
        AutoPickupSessions.beginDropContext(player, self.getBlockPos());
    }

    @Inject(method = "dropContent", at = @At("TAIL"))
    private void autopickup_closeBrushContext(ServerLevel level, LivingEntity brusher,
                                               ItemStack tool, CallbackInfo ci) {
        if (!(brusher instanceof Player player)) return;
        AutoPickupSessions.endDropContext(player);
    }
}
