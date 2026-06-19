package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {

    @Shadow @Final
    protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void autopickup_onTryBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        AutoPickupSessions.begin(this.player);
        AutoPickupSessions.addBreak(this.player, pos);
        // Open a drop context for the entire destroyBlock call so NeoForge's XP
        // pipeline (which runs after dropResources) reaches BlockDropExperienceMixin
        // without BlockMixin needing to cancel dropResources.
        AutoPickupSessions.beginDropContext(this.player, pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void autopickup_onBreakBlockReturn(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        AutoPickupSessions.endDropContext(this.player);
    }
}
