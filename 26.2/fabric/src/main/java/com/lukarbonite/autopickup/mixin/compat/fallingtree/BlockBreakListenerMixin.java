package com.lukarbonite.autopickup.mixin.compat.fallingtree;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import fr.rakambda.fallingtree.fabric.event.BlockBreakListener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockBreakListener.class, remap = false)
public class BlockBreakListenerMixin {

    @Inject(method = "beforeBlockBreak", at = @At("HEAD"), remap = false)
    private void onHead(Level level, Player player, BlockPos pos, BlockState state,
                        BlockEntity be, CallbackInfoReturnable<Boolean> cir) {
        AutoPickupApi.setBlockBreaker(player);
        AutoPickupSessions.begin(player);
        AutoPickupSessions.addBreak(player, pos);
    }

    @Inject(method = "beforeBlockBreak", at = @At("RETURN"), remap = false)
    private void onReturn(Level level, Player player, BlockPos pos, BlockState state,
                          BlockEntity be, CallbackInfoReturnable<Boolean> cir) {
        AutoPickupApi.clearBlockBreaker();
    }
}
