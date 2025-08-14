package com.lukarbonite.autopickup.mixin;

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
        ExperienceCache.onServerTick((MinecraftServer) (Object) this);
    }
}