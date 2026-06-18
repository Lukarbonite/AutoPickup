package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.ExperienceCache;
import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public class BlockDropExperienceMixin {

    @Inject(method = "popExperience", at = @At("HEAD"), cancellable = true)
    private void autopickup_captureAndCacheExperience(ServerLevel world, BlockPos pos, int size, CallbackInfo ci) {
        Vec3 posCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        Player player = AutoPickupSessions.findOwnerInDropContext(posCenter);
        if (player == null) player = AutoPickupSessions.findOwnerByBreakPos(posCenter);

        if (player != null && !world.isClientSide()
                && AutoPickupApi.isMasterEnabled(player)
                && AutoPickupApi.isBlockXpEnabled(player)) {
            ExperienceCache.add(player, size);
            ci.cancel();
        }
    }
}