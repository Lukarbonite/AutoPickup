package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.LootSplittingLogic;
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

    // Redirect XP Drop
    @Redirect(
            method = "dropExperience(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ExperienceOrbEntity;spawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/Vec3d;I)V")
    )
    private void autopickup_redirectExperience(ServerWorld world, Vec3d pos, int amount, ServerWorld originalWorld, Entity attacker) {
        if (attacker instanceof PlayerEntity player && !player.isSpectator()) {
            LivingEntity mob = (LivingEntity) (Object) this;

            // Use the Splitting Logic to distribute (or give to killer)
            LootSplittingLogic.distributeXp(player, mob, amount);
        } else {
            ExperienceOrbEntity.spawn(world, pos, amount);
        }
    }

    // Intercept Loot Drop
    @Inject(
            method = "dropLoot(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void autopickup_onDropLoot(ServerWorld world, DamageSource damageSource, boolean causedByPlayer, CallbackInfo ci) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;
        Entity attacker = damageSource.getAttacker();

        if (attacker instanceof PlayerEntity player && !player.isSpectator()) {

            if (!AutoPickupApi.isMasterEnabled(player)) return;

            // Generate Loot manually
            Optional<RegistryKey<LootTable>> optional = thisEntity.getLootTableKey();
            if (optional.isEmpty()) return;

            LootTable lootTable = world.getServer().getReloadableRegistries().getLootTable(optional.get());

            LootWorldContext.Builder builder = new LootWorldContext.Builder(world)
                    .add(LootContextParameters.THIS_ENTITY, thisEntity)
                    .add(LootContextParameters.ORIGIN, thisEntity.getEntityPos())
                    .add(LootContextParameters.DAMAGE_SOURCE, damageSource)
                    .addOptional(LootContextParameters.ATTACKING_ENTITY, damageSource.getAttacker())
                    .addOptional(LootContextParameters.DIRECT_ATTACKING_ENTITY, damageSource.getSource());

            if (causedByPlayer) {
                builder.add(LootContextParameters.LAST_DAMAGE_PLAYER, player).luck(player.getLuck());
            }

            LootWorldContext lootContext = builder.build(LootContextTypes.ENTITY);
            List<ItemStack> generatedLoot = new ArrayList<>();
            lootTable.generateLoot(lootContext, thisEntity.getLootTableSeed(), generatedLoot::add);

            // Distribute via Splitting Logic
            List<ItemStack> remainingItems = LootSplittingLogic.distributeLoot(player, thisEntity, generatedLoot);

            // Drop whatever wasn't picked up
            for (ItemStack stack : remainingItems) {
                thisEntity.dropStack(world, stack);
            }

            ci.cancel();
        }
    }
}