package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickup;
import com.lukarbonite.autopickup.AutoPickupApi;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public class BlockDropExperienceMixin {

    @Inject(method = "dropExperience", at = @At("HEAD"), cancellable = true)
    private void autopickup_captureExperience(ServerWorld world, BlockPos pos, int size, CallbackInfo ci) {
        // Check if there is a player context from our other mixins.
        PlayerEntity player = AutoPickupApi.getBlockBreaker();

        // If a player broke the block and the gamerule is on, give them the XP directly.
        if (player != null && !world.isClient() && world.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)) {
            AutoPickupApi.tryPickupExperience(player, size);
            // Cancel the original method to prevent the ExperienceOrbEntity from spawning.
            ci.cancel();
        }
    }
}