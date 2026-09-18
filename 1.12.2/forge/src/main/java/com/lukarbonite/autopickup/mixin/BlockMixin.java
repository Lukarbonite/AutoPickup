package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.lukarbonite.autopickup.ExperienceCache;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block XP in one piece, before vanilla splits it into orbs. Same role as BlockDropExperienceMixin
 * on newer versions. (Item drops are taken from Forge's HarvestDropsEvent in AutoPickupForge.)
 */
@Mixin(Block.class)
public abstract class BlockMixin {

    @Inject(method = "dropXpOnBlockBreak", at = @At("HEAD"), cancellable = true)
    private void autopickup_captureExperience(World world, BlockPos pos, int amount, CallbackInfo ci) {
        if (world.isRemote) return;
        EntityPlayer player = AutoPickupSessions.findOwner(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));

        if (player != null && AutoPickupApi.isMasterEnabled(player) && AutoPickupApi.isBlockXpEnabled(player)) {
            ExperienceCache.add(player, amount);
            ci.cancel();
        }
    }
}
