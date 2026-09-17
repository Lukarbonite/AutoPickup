package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sheep, snow golems, and mooshrooms drop their sheared items via spawnAtLocation()
 * inside Mob#mobInteract when right-clicked with shears. Mob#interact calls
 * super.interact() (Entity#interact) before mobInteract(), so the drop context must
 * be opened around the whole Mob#interact — opening it on Entity#interact would close
 * it again before mobInteract runs and the items spawn.
 */
@Mixin(Mob.class)
public abstract class ShearMixin {

    @Inject(method = "interact", at = @At("HEAD"))
    private void autopickup_openShearContext(Player player, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
        Mob self = (Mob) (Object) this;
        if (self.level().isClientSide()) return;
        if (!player.getItemInHand(hand).is(Items.SHEARS)) return;
        AutoPickupSessions.begin(player);
        AutoPickupSessions.beginDropContext(player, self.blockPosition());
    }

    @Inject(method = "interact", at = @At("RETURN"))
    private void autopickup_closeShearContext(Player player, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
        Mob self = (Mob) (Object) this;
        if (self.level().isClientSide()) return;
        if (!player.getItemInHand(hand).is(Items.SHEARS)) return;
        AutoPickupSessions.endDropContext(player);
    }
}
