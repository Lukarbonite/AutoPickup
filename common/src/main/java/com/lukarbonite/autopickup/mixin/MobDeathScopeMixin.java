package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.MobDeathDrops;
import com.lukarbonite.autopickup.platform.VersionHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opens a {@link MobDeathDrops} scope for the whole of LivingEntity#die. Forge/NeoForge fire
 * LivingDeathEvent at the top of die(), and mods like Butchery spawn extra drops (carcasses) from
 * that handler with addFreshEntity, bypassing the loot table that MobLootMixin intercepts.
 */
@Mixin(LivingEntity.class)
public abstract class MobDeathScopeMixin {

    @Inject(method = "die", at = @At("HEAD"))
    private void autopickup_openDeathScope(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (VersionHelper.INSTANCE.getLevel(self).isClientSide()) return;
        MobDeathDrops.begin(self, source);
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void autopickup_closeDeathScope(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (VersionHelper.INSTANCE.getLevel(self).isClientSide()) return;
        MobDeathDrops.end();
    }
}
