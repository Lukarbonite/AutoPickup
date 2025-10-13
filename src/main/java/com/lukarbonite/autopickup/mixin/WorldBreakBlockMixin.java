package com.lukarbonite.autopickup.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends the tight per-player drop context to blocks broken by mods that bypass
 * Block.dropStacks with a PlayerEntity (e.g., veinminer/liteminer). We link such breaks
 * to the nearest active mining session within a small radius and open a scoped drop context
 * only for the duration of the break call.
 */
@Mixin(World.class)
public abstract class WorldBreakBlockMixin {

    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void autopickup_openLinkedContext(BlockPos pos, boolean drop, Entity breaker, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        // Only consider cases where drops will be spawned and the breaker is NOT a player
        if (!drop || breaker instanceof PlayerEntity) return;
        // Open a temporary, linked drop context for a nearby active mining session (if any)
        com.lukarbonite.autopickup.AutoPickupSessions.openLinkedDropContext(pos);
    }

    @Inject(method = "breakBlock", at = @At("TAIL"))
    private void autopickup_closeLinkedContext(BlockPos pos, boolean drop, Entity breaker, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (!drop || breaker instanceof PlayerEntity) return;
        // Close the context opened at HEAD (if any). This balances nested calls via a thread-local stack.
        com.lukarbonite.autopickup.AutoPickupSessions.closeLinkedDropContext();
    }
}
