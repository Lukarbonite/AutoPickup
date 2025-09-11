package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.ExperienceCache;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class ServerTickMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onServerTickEnd(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        // Process the experience cache at the end of the tick.
        ExperienceCache.onServerTick((MinecraftServer) (Object) this);

        // Clear the block breaker context at the end of every tick.
        // This ensures the context from mods like Liteminer doesn't leak into the next tick.
        AutoPickupApi.clearBlockBreaker();
    }
}