package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickupApi;
import com.lukarbonite.autopickup.LootSplittingLogic;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(LivingEntity.class)
public abstract class MobLootMixin {

    @Shadow protected Player lastHurtByPlayer;
    @Shadow public abstract int getExperienceReward();
    @Shadow public abstract ResourceLocation getLootTable();
    @Shadow public abstract long getLootTableSeed();

    // Redirect XP Drop
    @Inject(
            method = "dropExperience",
            at = @At("HEAD"),
            cancellable = true
    )
    private void autopickup_redirectExperience(CallbackInfo ci) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;

        if (this.lastHurtByPlayer != null && !this.lastHurtByPlayer.isSpectator()) {

            // Check Master AND MobXpEnabled.
            // If disabled, fall back to Vanilla spawning immediately to prevent vanishing XP.
            if (AutoPickupApi.isMasterEnabled(this.lastHurtByPlayer) && AutoPickupApi.isMobXpEnabled(this.lastHurtByPlayer)) {
                LootSplittingLogic.distributeXp(this.lastHurtByPlayer, thisEntity, this.getExperienceReward());
                ci.cancel(); // Prevent ExperienceOrb.award from being called
            }
        }
    }

    // Intercept Loot Drop
    @Inject(
            method = "dropFromLootTable(Lnet/minecraft/world/damagesource/DamageSource;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void autopickup_onDropLoot(DamageSource damageSource, boolean causedByPlayer, CallbackInfo ci) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;

        // Use the internal level field and cast to ServerLevel
        if (!(thisEntity.level() instanceof ServerLevel world)) {
            return;
        }

        // The source code uses damageSource.getEntity() to determine the killer
        if (damageSource.getEntity() instanceof Player player && !player.isSpectator()) {

            // Check Master AND MobLootEnabled.
            // If disabled, ignore interception and let vanilla drop loot normally.
            if (!AutoPickupApi.isMasterEnabled(player) || !AutoPickupApi.isMobLootEnabled(player)) {
                return;
            }

            // Generate Loot manually
            ResourceLocation lootTableLocation = this.getLootTable();
            LootTable lootTable = world.getServer().getLootData().getLootTable(lootTableLocation);

            if (lootTable == LootTable.EMPTY) {
                return;
            }

            // Mimicking the builder parameters found in the source
            LootParams.Builder builder = new LootParams.Builder(world)
                    .withParameter(LootContextParams.THIS_ENTITY, thisEntity)
                    .withParameter(LootContextParams.ORIGIN, thisEntity.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
                    .withOptionalParameter(LootContextParams.KILLER_ENTITY, damageSource.getEntity())
                    .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, damageSource.getDirectEntity());

            if (causedByPlayer && this.lastHurtByPlayer != null) {
                builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, this.lastHurtByPlayer)
                        .withLuck(this.lastHurtByPlayer.getLuck());
            }

            LootParams lootParams = builder.create(LootContextParamSets.ENTITY);
            List<ItemStack> generatedLoot = new ArrayList<>();
            lootTable.getRandomItems(lootParams, this.getLootTableSeed(), generatedLoot::add);

            // Distribute
            List<ItemStack> remainingItems = LootSplittingLogic.distributeLoot(player, thisEntity, generatedLoot);

            // Drop whatever wasn't picked up
            for (ItemStack stack : remainingItems) {
                thisEntity.spawnAtLocation(stack);
            }

            // Cancel original dropFromLootTable logic
            ci.cancel();
        }
    }
}