package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.MobLootCompat; // Import the new mob compat class
import com.lukarbonite.autopickup.VeinminerCompat;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class ServerTickMixin {

    @Inject(method = "tick", at = @At("TAIL")) // Changed to TAIL for end-of-tick processing
    private void onServerTickEnd(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        // Process Veinminer XP cache
        VeinminerCompat.onServerTick(server);
        // Process Mob Loot XP cache
        MobLootCompat.onServerTickEnd(server);
    }
}