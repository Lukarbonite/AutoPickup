package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.LootSplittingLogic;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// TODO(Ravel): can not resolve target class LivingEntity
// TODO(Ravel): can not resolve target class LivingEntity
// TODO(Ravel): can not resolve target class LivingEntity
// TODO(Ravel): can not resolve target class LivingEntity
@Mixin(LivingEntity.class)
public abstract class MobLootMixin {

    // TODO(Ravel): no target class
// TODO(Ravel): no target class
// TODO(Ravel): no target class
// TODO(Ravel): no target class
// 1. Redirect XP Drop
    @Redirect(
            method = "dropExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V")
    )
    private void autopickup_redirectExperience(ServerLevel world, Vec3 pos, int amount, ServerLevel originalWorld, Entity attacker) {
        if (attacker instanceof Player player && !player.isSpectator()) {

            // Check Master AND MobXpEnabled.
            // If disabled, fall back to Vanilla spawning immediately to prevent vanishing XP.
            if (AutoPickupApi.isMasterEnabled(player) && AutoPickupApi.isMobXpEnabled(player)) {
                LootSplittingLogic.distributeXp(player, (LivingEntity) (Object) this, amount);
                return;
            }
        }

        // Fallback: Drop on ground (Vanilla behavior)
        ExperienceOrb.award(world, pos, amount);
    }

    // TODO(Ravel): no target class
// TODO(Ravel): no target class
// TODO(Ravel): no target class
// TODO(Ravel): no target class
// 2. Intercept Loot Drop
    @Inject(
            method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void autopickup_onDropLoot(ServerLevel world, DamageSource damageSource, boolean causedByPlayer, CallbackInfo ci) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;
        Entity attacker = damageSource.getEntity();

        if (attacker instanceof Player player && !player.isSpectator()) {

            // Check Master AND MobLootEnabled.
            // If disabled, ignore interception and let vanilla drop loot normally.
            if (!AutoPickupApi.isMasterEnabled(player) || !AutoPickupApi.isMobLootEnabled(player)) {
                return;
            }

            // Generate Loot manually
            Optional<ResourceKey<LootTable>> optional = thisEntity.getLootTable();
            if (optional.isEmpty()) return;

            LootTable lootTable = world.getServer().reloadableRegistries().getLootTable(optional.get());

            LootParams.Builder builder = new LootParams.Builder(world)
                    .withParameter(LootContextParams.THIS_ENTITY, thisEntity)
                    .withParameter(LootContextParams.ORIGIN, thisEntity.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
                    .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, damageSource.getEntity())
                    .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, damageSource.getDirectEntity());

            if (causedByPlayer) {
                builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player).withLuck(player.getLuck());
            }

            LootParams lootContext = builder.create(LootContextParamSets.ENTITY);
            List<ItemStack> generatedLoot = new ArrayList<>();
            lootTable.getRandomItems(lootContext, thisEntity.getLootTableSeed(), generatedLoot::add);

            // Distribute
            List<ItemStack> remainingItems = LootSplittingLogic.distributeLoot(player, thisEntity, generatedLoot);

            // Drop whatever wasn't picked up
            for (ItemStack stack : remainingItems) {
                thisEntity.spawnAtLocation(world, stack);
            }

            // Cancel original dropLoot
            ci.cancel();
        }
    }
}