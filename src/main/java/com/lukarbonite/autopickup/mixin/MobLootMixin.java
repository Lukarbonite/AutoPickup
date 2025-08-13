package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickup;
import com.lukarbonite.autopickup.AutoPickupApi;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(LivingEntity.class)
public abstract class MobLootMixin {

    // This mixin redirects the spawning of experience orbs.
    @Redirect(
            method = "dropExperience(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ExperienceOrbEntity;spawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/Vec3d;I)V")
    )
    private void autopickup_redirectExperience(ServerWorld world, Vec3d pos, int amount, ServerWorld originalWorld, Entity attacker) {
        // We get the player from the 'attacker' parameter passed to the original method. This is safe.
        if (attacker instanceof PlayerEntity player && world.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_MOB_LOOT_GAMERULE_KEY)) {
            AutoPickupApi.tryPickupExperience(player, amount);
        } else {
            // If the killer isn't a player or the rule is off, spawn the orb normally.
            ExperienceOrbEntity.spawn(world, pos, amount);
        }
    }

    // This mixin hijacks the entire loot drop process.
    @Inject(
            method = "dropLoot(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void autopickup_onDropLoot(ServerWorld world, DamageSource damageSource, boolean causedByPlayer, CallbackInfo ci) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;
        // Get the attacker directly from the damage source parameter. This is the safest way and avoids NoSuchMethodError.
        Entity attacker = damageSource.getAttacker();

        // Check if the attacker is a player and our other conditions are met.
        if (attacker instanceof PlayerEntity player && !player.isSpectator() && world.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_MOB_LOOT_GAMERULE_KEY)) {
            Optional<RegistryKey<LootTable>> optional = thisEntity.getLootTableKey();
            if (optional.isEmpty()) {
                return; // Nothing to drop.
            }

            LootTable lootTable = world.getServer().getReloadableRegistries().getLootTable(optional.get());

            // Build the context correctly.
            LootWorldContext.Builder builder = new LootWorldContext.Builder(world)
                    .add(LootContextParameters.THIS_ENTITY, thisEntity)
                    .add(LootContextParameters.ORIGIN, thisEntity.getPos())
                    .add(LootContextParameters.DAMAGE_SOURCE, damageSource)
                    .addOptional(LootContextParameters.ATTACKING_ENTITY, damageSource.getAttacker())
                    .addOptional(LootContextParameters.DIRECT_ATTACKING_ENTITY, damageSource.getSource());

            if (causedByPlayer) {
                builder.add(LootContextParameters.LAST_DAMAGE_PLAYER, player).luck(player.getLuck());
            }

            LootWorldContext lootContext = builder.build(LootContextTypes.ENTITY);

            // Generate the loot into a temporary list.
            List<ItemStack> generatedLoot = new ArrayList<>();
            lootTable.generateLoot(lootContext, thisEntity.getLootTableSeed(), generatedLoot::add);

            // Give the items to the player.
            List<ItemStack> remainingItems = AutoPickupApi.tryPickupFromMob(player, generatedLoot);

            // Drop any leftovers at the player.
            for (ItemStack stack : remainingItems) {
                player.dropItem(stack, true);
            }

            // Cancel the original method.
            ci.cancel();
        }
    }
}