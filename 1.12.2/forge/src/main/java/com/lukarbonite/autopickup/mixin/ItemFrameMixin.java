package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hitting an item frame drops its item (and the frame) for the player who hit it. */
@Mixin(EntityItemFrame.class)
public abstract class ItemFrameMixin {

    @Inject(method = "attackEntityFrom", at = @At("HEAD"))
    private void autopickup_openFrameContext(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!(source.getTrueSource() instanceof EntityPlayerMP)) return;
        EntityItemFrame self = (EntityItemFrame) (Object) this;
        AutoPickupSessions.openUseContext((EntityPlayerMP) source.getTrueSource(), new BlockPos(self));
    }

    // RETURN + the same player guard as HEAD: attackEntityFrom has several early returns, and an
    // unconditional close could pop an outer use context.
    @Inject(method = "attackEntityFrom", at = @At("RETURN"))
    private void autopickup_closeFrameContext(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!(source.getTrueSource() instanceof EntityPlayerMP)) return;
        AutoPickupSessions.closeUseContext();
    }
}
