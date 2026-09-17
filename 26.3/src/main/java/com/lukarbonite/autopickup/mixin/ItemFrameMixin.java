package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrame.class)
public abstract class ItemFrameMixin {

    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void autopickup_openFrameContext(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        ItemFrame self = (ItemFrame) (Object) this;
        AutoPickupSessions.openUseContext(player, self.blockPosition());
    }

    // RETURN + same player guard as HEAD: hurtServer has many early-return paths, and TAIL only
    // covers the last one — early returns would leak the context (and an unconditional close
    // could pop an outer use context). Balancing open/close on the player condition keeps the
    // context scoped to the actual player interaction.
    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void autopickup_closeFrameContext(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!(source.getEntity() instanceof ServerPlayer)) return;
        AutoPickupSessions.closeUseContext();
    }
}
