package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickup;
import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.ExperienceCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public class BlockDropExperienceMixin {

    @Inject(method = "popExperience", at = @At("HEAD"), cancellable = true)
    private void autopickup_captureAndCacheExperience(ServerLevel world, BlockPos pos, int size, CallbackInfo ci) {
        // Attribute block XP to the nearest active mining session at this position.
        net.minecraft.world.phys.Vec3 posCenter = new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Player player = com.lukarbonite.autopickup.AutoPickupSessions.findOwner(posCenter);

        // Check master rule first, then the specific XP rule.
        if (player != null && !world.isClientSide()
                && world.getGameRules().get(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)
                && world.getGameRules().get(AutoPickup.AUTO_PICKUP_XP_GAMERULE_KEY)) {

            // Always funnel the experience into the universal handler.
            ExperienceCache.add(player, size);

            // Cancel the original method. The handler is now responsible for the XP.
            ci.cancel();
        }
    }
}