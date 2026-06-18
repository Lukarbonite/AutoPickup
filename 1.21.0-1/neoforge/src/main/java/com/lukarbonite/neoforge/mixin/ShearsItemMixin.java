package com.lukarbonite.neoforge.mixin;

import com.lukarbonite.autopickup.AutoPickupSessions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * On NeoForge, vanilla shearing is removed from Mob#mobInteract and routed through
 * {@code ShearsItem#interactLivingEntity}, which calls IShearable#onSheared and spawns
 * the resulting drops via IShearable#spawnShearedDrop -> Entity#spawnAtLocation
 * (-> addFreshEntity). The shared ShearMixin (on Mob#interact) never wraps this, so
 * open a tight drop context here to attribute those drops to the shearing player.
 */
@Mixin(ShearsItem.class)
public abstract class ShearsItemMixin {

    @Inject(method = "interactLivingEntity", at = @At("HEAD"))
    private void autopickup_openShearContext(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (entity.level().isClientSide()) return;
        AutoPickupSessions.begin(player);
        AutoPickupSessions.beginDropContext(player, entity.blockPosition());
    }

    @Inject(method = "interactLivingEntity", at = @At("RETURN"))
    private void autopickup_closeShearContext(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (entity.level().isClientSide()) return;
        AutoPickupSessions.endDropContext(player);
    }
}
