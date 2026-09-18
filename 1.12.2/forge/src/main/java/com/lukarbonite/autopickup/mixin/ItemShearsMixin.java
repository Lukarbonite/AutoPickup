package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge routes shearing sheep, snow golems and mooshrooms through IShearable from
 * {@code ItemShears#itemInteractionForEntity}, which drops the results next to the entity.
 * Same role as the Forge ShearsItemMixin on newer versions.
 */
@Mixin(ItemShears.class)
public abstract class ItemShearsMixin {

    @Inject(method = "itemInteractionForEntity", at = @At("HEAD"))
    private void autopickup_openShearContext(ItemStack stack, EntityPlayer player, EntityLivingBase entity, EnumHand hand,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (entity.world.isRemote) return;
        AutoPickupSessions.begin(player);
        AutoPickupSessions.beginDropContext(player, new BlockPos(entity));
    }

    @Inject(method = "itemInteractionForEntity", at = @At("RETURN"))
    private void autopickup_closeShearContext(ItemStack stack, EntityPlayer player, EntityLivingBase entity, EnumHand hand,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (entity.world.isRemote) return;
        AutoPickupSessions.endDropContext(player);
    }
}
