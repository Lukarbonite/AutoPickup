package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.MobDeathDrops;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Opens a {@link MobDeathDrops} scope around a mob's death. Same role as common's
 * MobDeathScopeMixin on newer versions, but 1.12.2 subclasses (horses, donkeys, llamas, pigs)
 * drop extra items after super.onDeath() returns, outside LivingDropsEvent.
 * So the scope wraps the virtual onDeath call in attackEntityFrom, which covers the whole override.
 * The onDeath HEAD/RETURN pair covers mods that call onDeath directly; nesting is harmless.
 */
@Mixin(EntityLivingBase.class)
public abstract class MobDeathScopeMixin {

    @Inject(method = "attackEntityFrom",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;onDeath(Lnet/minecraft/util/DamageSource;)V"))
    private void autopickup_openOuterDeathScope(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (self.world.isRemote) return;
        MobDeathDrops.begin(self, source);
    }

    @Inject(method = "attackEntityFrom",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;onDeath(Lnet/minecraft/util/DamageSource;)V",
                    shift = At.Shift.AFTER))
    private void autopickup_closeOuterDeathScope(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (self.world.isRemote) return;
        MobDeathDrops.end();
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void autopickup_openDeathScope(DamageSource source, CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (self.world.isRemote) return;
        MobDeathDrops.begin(self, source);
    }

    @Inject(method = "onDeath", at = @At("RETURN"))
    private void autopickup_closeDeathScope(DamageSource source, CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (self.world.isRemote) return;
        MobDeathDrops.end();
    }
}
