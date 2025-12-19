package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickup;
import com.lukarbonite.autopickup.ExperienceCache;
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
    private void autopickup_captureAndCacheExperience(ServerWorld world, BlockPos pos, int size, CallbackInfo ci) {
        // Attribute block XP to the nearest active mining session at this position.
        net.minecraft.util.math.Vec3d posCenter = new net.minecraft.util.math.Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        PlayerEntity player = com.lukarbonite.autopickup.AutoPickupSessions.findOwner(posCenter);

        // Check master rule first, then the specific XP rule.
        if (player != null && !world.isClient()
                && world.getGameRules().getValue(AutoPickup.AUTO_PICKUP_GAMERULE_KEY)
                && world.getGameRules().getValue(AutoPickup.AUTO_PICKUP_XP_GAMERULE_KEY)) {

            // Always funnel the experience into the universal handler.
            ExperienceCache.add(player, size);

            // Cancel the original method. The handler is now responsible for the XP.
            ci.cancel();
        }
    }
}