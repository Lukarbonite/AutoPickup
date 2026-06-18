package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In 1.21.1, armadillo brushing is handled by Armadillo#brushOffScute(), which calls
 * spawnAtLocation() directly without going through a loot table (unlike 1.21.9-11+'s
 * LivingEntity#dropFromEntityInteractLootTable). brushOffScute() is called synchronously
 * from Armadillo#mobInteract(), which Mob#interact() (final) dispatches to - Entity#interact
 * is never reached for Mob subclasses, so mixin into Mob#interact instead.
 */
@Mixin(Mob.class)
public abstract class BrushEntityMixin {

    @Inject(method = "interact", at = @At("HEAD"))
    private void autopickup_openBrushEntityContext(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Armadillo) || self.level().isClientSide) return;
        AutoPickupSessions.begin(player);
        AutoPickupSessions.beginDropContext(player, self.blockPosition());
    }

    @Inject(method = "interact", at = @At("RETURN"))
    private void autopickup_closeBrushEntityContext(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Armadillo) || self.level().isClientSide) return;
        AutoPickupSessions.endDropContext(player);
    }
}
