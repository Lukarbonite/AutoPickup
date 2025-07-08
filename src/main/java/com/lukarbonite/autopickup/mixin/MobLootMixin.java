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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(LivingEntity.class)
public abstract class MobLootMixin {

    // This is the only Shadow we need, and it is guaranteed to exist directly on LivingEntity.
    @Shadow public abstract PlayerEntity getAttackingPlayer();

    /**
     * Intercepts the spawning of experience orbs.
     */
    @Redirect(
            method = "dropExperience(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ExperienceOrbEntity;spawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/Vec3d;I)V")
    )
    private void autopickup_redirectSpawnExperience(ServerWorld world, Vec3d pos, int amount) {
        PlayerEntity player = this.getAttackingPlayer();
        if (player != null && world.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_MOB_LOOT_GAMERULE_KEY)) {
            player.addExperience(amount);
        } else {
            ExperienceOrbEntity.spawn(world, pos, amount);
        }
    }

    /**
     * This is the corrected implementation that hijacks the entire loot-dropping process.
     * It avoids the @Shadow issue by calling methods directly on the entity instance.
     */
    @Inject(method = "dropLoot(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;Z)V", at = @At("HEAD"), cancellable = true)
    private void autopickup_onDropLoot(ServerWorld world, DamageSource damageSource, boolean causedByPlayer, CallbackInfo ci) {
        // In an instance method mixin, 'this' refers to the LivingEntity instance.
        LivingEntity thisEntity = (LivingEntity)(Object)this;
        PlayerEntity player = thisEntity.getAttackingPlayer();

        // Check if a player is responsible and if the gamerule is enabled.
        if (player != null && !player.isSpectator() && world.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_MOB_LOOT_GAMERULE_KEY)) {
            // Call the methods directly on the instance, which is more robust than @Shadow for inherited methods.
            Optional<RegistryKey<LootTable>> optional = thisEntity.getLootTableKey();
            if (optional.isEmpty()) {
                return; // Nothing to drop
            }

            LootTable lootTable = world.getServer().getReloadableRegistries().getLootTable(optional.get());

            // Correctly build the LootWorldContext.
            LootWorldContext.Builder builder = new LootWorldContext.Builder(world)
                    .add(LootContextParameters.THIS_ENTITY, thisEntity)
                    .add(LootContextParameters.ORIGIN, thisEntity.getPos())
                    .add(LootContextParameters.DAMAGE_SOURCE, damageSource)
                    .addOptional(LootContextParameters.ATTACKING_ENTITY, damageSource.getAttacker())
                    .addOptional(LootContextParameters.DIRECT_ATTACKING_ENTITY, damageSource.getSource());

            if (causedByPlayer && player != null) {
                builder.add(LootContextParameters.LAST_DAMAGE_PLAYER, player).luck(player.getLuck());
            }

            LootWorldContext lootWorldContext = builder.build(LootContextTypes.ENTITY);

            // Generate the loot into a temporary list.
            List<ItemStack> generatedLoot = new ArrayList<>();
            lootTable.generateLoot(lootWorldContext, thisEntity.getLootTableSeed(), generatedLoot::add);

            // Use our API to give the items to the player.
            List<ItemStack> remainingItems = AutoPickupApi.tryPickupFromMob(player, generatedLoot);

            // Have the player drop any items that didn't fit in their inventory.
            for (ItemStack stack : remainingItems) {
                player.dropItem(stack, true);
            }

            // Cancel the original method to prevent the game from dropping the loot a second time.
            ci.cancel();
        }
    }
}