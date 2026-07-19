package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupApi;
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

    /**
     * This is the earliest reliable point to capture the player breaking a block.
     * By injecting here, player context is set for the entire tick. This context
     * will then be available to all subsequent events and methods called by vanilla
     * or other mods like Liteminer during this tick.
     */
    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void autopickup_onTryBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // Mark that we are on this player's break stack for the whole call, so synchronous non-player
        // breaks/drops it causes (LiteMiner vein, bamboo cascade) are attributed while independent
        // world-tick farms are not. Balanced by exitPlayerBreak at RETURN.
        com.lukarbonite.autopickup.AutoPickupSessions.enterPlayerBreak(this.player);
        // Start or refresh a per-player mining session and record this break position.
        com.lukarbonite.autopickup.AutoPickupSessions.begin(this.player);
        com.lukarbonite.autopickup.AutoPickupSessions.addBreak(this.player, pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void autopickup_onBreakBlockReturn(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        com.lukarbonite.autopickup.AutoPickupSessions.exitPlayerBreak();
    }
}