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
import org.jetbrains.annotations.Nullable;
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
            method = "dropExperience",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V")
    )
    private void autopickup_redirectExperience(ServerLevel level, Vec3 pos, int amount, @Nullable Entity attacker) {
        if (attacker instanceof Player player && !player.isSpectator()
                && AutoPickupApi.isMasterEnabled(player)
                && AutoPickupApi.isMobXpEnabled(player)) {
            LootSplittingLogic.distributeXp(player, (LivingEntity) (Object) this, amount);
            return;
        }
        ExperienceOrb.award(level, pos, amount);
    }

    @Inject(
            method = "dropFromLootTable",
            at = @At("HEAD"),
            cancellable = true
    )
    private void autopickup_onDropFromLootTable(DamageSource damageSource, boolean causedByPlayer, CallbackInfo ci) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;
        if (!(thisEntity.level() instanceof ServerLevel level)) return;

        Entity attacker = damageSource.getEntity();
        if (!(attacker instanceof Player player) || player.isSpectator()) return;
        if (!AutoPickupApi.isMasterEnabled(player) || !AutoPickupApi.isMobLootEnabled(player)) return;

        ResourceKey<LootTable> lootTableKey = thisEntity.getLootTable();
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);

        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, thisEntity)
                .withParameter(LootContextParams.ORIGIN, thisEntity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, damageSource.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, damageSource.getDirectEntity());

        if (causedByPlayer) {
            builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player).withLuck(player.getLuck());
        }

        LootParams lootParams = builder.create(LootContextParamSets.ENTITY);
        List<ItemStack> generatedLoot = new ArrayList<>();
        lootTable.getRandomItems(lootParams, thisEntity.getLootTableSeed(), generatedLoot::add);

        List<ItemStack> remainingItems = LootSplittingLogic.distributeLoot(player, thisEntity, generatedLoot);

        for (ItemStack stack : remainingItems) {
            thisEntity.spawnAtLocation(stack);
        }

        ci.cancel();
    }
}
