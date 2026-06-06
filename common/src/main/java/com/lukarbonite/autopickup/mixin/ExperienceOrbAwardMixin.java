package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.lukarbonite.autopickup.ExperienceCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrb.class)
public class ExperienceOrbAwardMixin {

    // Backup capture for VeinMiner (and similar) on NeoForge when XP bypasses
    // Block.popExperience and goes directly to ExperienceOrb.award().
    // Primary:    findOwnerInDropContext — tight per-block scope, synchronous XP.
    // Secondary:  findOwnerByBreakPos   — exact position match, covers deferred XP after
    //             endDropContext when the VeinMiner mixin successfully registers the position.
    // Tertiary:   findOwner             — 6-block session radius, last resort for cases where
    //             the VeinMiner mixin's private-method injection did not fire (e.g. distant
    //             vein blocks whose position was never added to BREAK_OWNERS).
    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private static void autopickup_captureVeinMinerXp(ServerLevel level, Vec3 pos, int amount, CallbackInfo ci) {
        Player player = AutoPickupSessions.findOwnerInDropContext(pos);
        if (player == null) player = AutoPickupSessions.findOwnerByBreakPos(pos);
        if (player == null) player = AutoPickupSessions.findOwner(pos);
        if (player == null) return;

        if (AutoPickupApi.isMasterEnabled(player) && AutoPickupApi.isBlockXpEnabled(player)) {
            ExperienceCache.add(player, amount);
            ci.cancel();
        }
    }
}
