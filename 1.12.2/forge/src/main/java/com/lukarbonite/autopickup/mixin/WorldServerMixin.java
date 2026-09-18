package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.AutoPickupSessions;
import com.lukarbonite.autopickup.ExperienceCache;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

/**
 * Every item and XP orb a server world spawns passes through {@code spawnEntity}. Same role as
 * ServerLevelMixin (items) and ExperienceOrbAwardMixin (XP) on newer versions: catches drops that
 * bypass the harvest event, such as blocks broken by vein miners or popped by the player's break.
 *
 * <p>Forge's EntityJoinWorldEvent would also see these, but it fires for chunk loads too.
 */
@Mixin(WorldServer.class)
public abstract class WorldServerMixin {

    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void autopickup_interceptSpawns(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof EntityItem) {
            interceptItem((EntityItem) entity, cir);
        } else if (entity instanceof EntityXPOrb) {
            interceptExperience((EntityXPOrb) entity, cir);
        }
    }

    private static void interceptItem(EntityItem itemEntity, CallbackInfoReturnable<Boolean> cir) {
        // Skip items with an owner: player hand-drops and the like
        if (itemEntity.getThrower() != null) return;

        Vec3d spawnPos = new Vec3d(itemEntity.posX, itemEntity.posY, itemEntity.posZ);

        // Only intercept if we are within an active player drop-context
        EntityPlayer owner = AutoPickupSessions.findOwnerInDropContext(spawnPos);
        // Same-tick tiny-radius fallback (for mods that spawn items directly)
        if (owner == null) owner = AutoPickupSessions.findOwnerSameTickTight(spawnPos);
        // Anything else spawned synchronously during the player's own break
        if (owner == null) owner = AutoPickupSessions.findOwner(spawnPos);
        // Right-click harvests
        if (owner == null) owner = AutoPickupSessions.findOwnerInUseContext(spawnPos);

        if (owner == null || owner.isSpectator()
                || !AutoPickupApi.isMasterEnabled(owner) || !AutoPickupApi.isBlocksEnabled(owner)) {
            return;
        }

        List<ItemStack> remainingItems = AutoPickupApi.tryPickup(owner, Collections.singletonList(itemEntity.getItem()));
        if (remainingItems.isEmpty()) {
            // Picked up in full: never spawn the entity.
            cir.setReturnValue(Boolean.TRUE);
        } else {
            // Inventory full: spawn what is left.
            itemEntity.setItem(remainingItems.get(0));
        }
    }

    // Primary:    findOwnerInDropContext (tight per-block scope, synchronous XP).
    // Secondary:  findOwnerByBreakPos (exact position of a recorded break, for deferred XP).
    // Tertiary:   findOwner (anything spawned synchronously during the player's own break).
    private static void interceptExperience(EntityXPOrb orb, CallbackInfoReturnable<Boolean> cir) {
        Vec3d pos = new Vec3d(orb.posX, orb.posY, orb.posZ);
        EntityPlayer player = AutoPickupSessions.findOwnerInDropContext(pos);
        if (player == null) player = AutoPickupSessions.findOwnerByBreakPos(pos);
        if (player == null) player = AutoPickupSessions.findOwner(pos);
        if (player == null) return;

        if (AutoPickupApi.isMasterEnabled(player) && AutoPickupApi.isBlockXpEnabled(player)) {
            ExperienceCache.add(player, orb.getXpValue());
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
