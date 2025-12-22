package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupApi;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {

    @Shadow
    public ServerPlayerEntity player;

    /**
     * This is the earliest reliable point to capture the player breaking a block.
     * By injecting here, player context is set for the entire tick. This context
     * will then be available to all subsequent events and methods called by vanilla
     * or other mods like Liteminer during this tick.
     */
    @Inject(method = "tryBreakBlock", at = @At("HEAD"))
    private void autopickup_onTryBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // Start or refresh a per-player mining session and record this break position.
        com.lukarbonite.autopickup.AutoPickupSessions.begin(this.player);
        com.lukarbonite.autopickup.AutoPickupSessions.addBreak(this.player, pos);
    }
}