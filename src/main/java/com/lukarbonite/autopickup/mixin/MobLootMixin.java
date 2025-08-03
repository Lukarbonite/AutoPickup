package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.AutoPickup;
import com.lukarbonite.autopickup.AutoPickupApi;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(LivingEntity.class)
public abstract class MobLootMixin {

    @Shadow protected PlayerEntity attackingPlayer;

    @Shadow public abstract int getXpToDrop();

    // This shadow should now work correctly with the import added.
    @Shadow public abstract Identifier getLootTable();

    @Shadow public abstract long getLootTableSeed();

    @Inject(method = "dropXp", at = @At("HEAD"), cancellable = true)
    private void autopickup_captureExperience(CallbackInfo ci) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;
        ServerWorld world = (ServerWorld) thisEntity.getWorld();

        if (!world.isClient() && world.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_MOB_LOOT_GAMERULE_KEY)) {
            if (this.attackingPlayer != null) {
                int xpAmount = this.getXpToDrop();
                if (xpAmount > 0) {
                    this.attackingPlayer.addExperience(xpAmount);
                }
                ci.cancel();
            }
        }
    }

    @Inject(
            method = "dropLoot(Lnet/minecraft/entity/damage/DamageSource;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void autopickup_onDropLoot(DamageSource damageSource, boolean causedByPlayer, CallbackInfo ci) {
        LivingEntity thisEntity = (LivingEntity) (Object) this;
        if (!(thisEntity.getWorld() instanceof ServerWorld world)) {
            return;
        }

        Entity attacker = damageSource.getAttacker();

        if (attacker instanceof PlayerEntity player && !player.isSpectator() && world.getGameRules().getBoolean(AutoPickup.AUTO_PICKUP_MOB_LOOT_GAMERULE_KEY)) {
            Identifier lootTableId = thisEntity.getLootTable();
            LootTable lootTable = world.getServer().getLootManager().getLootTable(lootTableId);

            LootContextParameterSet.Builder builder = new LootContextParameterSet.Builder(world)
                    .add(LootContextParameters.THIS_ENTITY, thisEntity)
                    .add(LootContextParameters.ORIGIN, thisEntity.getPos())
                    .add(LootContextParameters.DAMAGE_SOURCE, damageSource)
                    .addOptional(LootContextParameters.KILLER_ENTITY, damageSource.getAttacker())
                    .addOptional(LootContextParameters.DIRECT_KILLER_ENTITY, damageSource.getSource());

            if (causedByPlayer) {
                builder.add(LootContextParameters.LAST_DAMAGE_PLAYER, player).luck(player.getLuck());
            }

            LootContextParameterSet lootContextParameterSet = builder.build(LootContextTypes.ENTITY);

            List<ItemStack> generatedLoot = new ArrayList<>();
            lootTable.generateLoot(lootContextParameterSet, thisEntity.getLootTableSeed(), generatedLoot::add);

            List<ItemStack> remainingItems = AutoPickupApi.tryPickupFromMob(player, generatedLoot);

            for (ItemStack stack : remainingItems) {
                player.dropItem(stack, true);
            }

            ci.cancel();
        }
    }
}