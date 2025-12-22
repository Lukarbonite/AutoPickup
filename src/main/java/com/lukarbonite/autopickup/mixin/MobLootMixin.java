package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.ExperienceCache;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.context.LootContextParameterSet;
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

@Mixin(LivingEntity.class)
public abstract class MobLootMixin {

    @Redirect(
            method = "dropXp",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ExperienceOrbEntity;spawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/Vec3d;I)V")
    )
    private void autopickup_redirectAndCacheExperience(ServerWorld world, Vec3d pos, int amount, @org.jetbrains.annotations.Nullable Entity attacker) {
        if (attacker instanceof PlayerEntity player
                && AutoPickupApi.isMasterEnabled(player)
                && AutoPickupApi.isXpEnabled(player)) {

            ExperienceCache.add(player, amount);
        } else {
            ExperienceOrbEntity.spawn(world, pos, amount);
        }
    }

    @Inject(
            method = "dropLoot(Lnet/minecraft/entity/damage/DamageSource;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void autopickup_onDropLoot(DamageSource damageSource, boolean causedByPlayer, CallbackInfo ci) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;
        // The world needs to be retrieved from the entity instance
        if (!(thisEntity.getWorld() instanceof ServerWorld world)) {
            return;
        }

        Entity attacker = damageSource.getAttacker();

        // Check master rule first, then the specific mob loot rule.
        if (attacker instanceof PlayerEntity player && !player.isSpectator()
                && AutoPickupApi.isMasterEnabled(player)
                && AutoPickupApi.isMobLootEnabled(player)) {

            RegistryKey<LootTable> optional = thisEntity.getLootTable();

            LootTable lootTable = world.getServer().getReloadableRegistries().getLootTable(optional);

            LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(world)
                    .add(LootContextParameters.THIS_ENTITY, thisEntity)
                    .add(LootContextParameters.ORIGIN, thisEntity.getPos())
                    .add(LootContextParameters.DAMAGE_SOURCE, damageSource)
                    .addOptional(LootContextParameters.ATTACKING_ENTITY, damageSource.getAttacker())
                    .addOptional(LootContextParameters.DIRECT_ATTACKING_ENTITY, damageSource.getSource());

            if (causedByPlayer) {
                builder.add(LootContextParameters.LAST_DAMAGE_PLAYER, player).luck(player.getLuck());
            }

            LootContextParameterSet lootContext = builder.build(LootContextTypes.ENTITY);

            List<ItemStack> generatedLoot = new ArrayList<>();
            lootTable.generateLoot(lootContext, thisEntity.getLootTableSeed(), generatedLoot::add);

            List<ItemStack> remainingItems = AutoPickupApi.tryPickupFromMob(player, generatedLoot);

            for (ItemStack stack : remainingItems) {
                player.dropItem(stack, true);
            }

            ci.cancel();
        }
    }
}