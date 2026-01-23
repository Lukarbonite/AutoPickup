package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.accessor.DamageTrackerAccessor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Mixin(LivingEntity.class)
public class DamageTrackerMixin implements DamageTrackerAccessor {

    @Unique
    private final LinkedList<UUID> autopickup_attackers = new LinkedList<>();

    @Override
    public void autopickup_addAttacker(UUID playerUuid) {
        // Remove existing entry if present to prevent duplicates
        autopickup_attackers.remove(playerUuid);

        // Add to front (Most recent)
        autopickup_attackers.addFirst(playerUuid);

        // Limit to last 10 unique players
        if (autopickup_attackers.size() > 10) {
            autopickup_attackers.removeLast();
        }
    }

    @Override
    public List<UUID> autopickup_getAttackers() {
        return Collections.unmodifiableList(autopickup_attackers);
    }

    @Inject(method = "damage", at = @At("HEAD"))
    private void autopickup_onDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.getAttacker() instanceof PlayerEntity player) {
            autopickup_addAttacker(player.getUuid());
        }
    }
}