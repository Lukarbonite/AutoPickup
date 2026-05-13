package com.lukarbonite.forge.mixin;

import net.minecraftforge.fml.loading.LoadingModList; // Standard Forge check
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ForgeMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
        // Loom handles this
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.treeharvester")) {
            return LoadingModList.get().getModFileById("treeharvester") != null;
        }
        if (mixinClassName.contains(".compat.travelersbackpack")) {
            if (LoadingModList.get().getModFileById("travelersbackpack") == null) return false;
            // AutoPickupTBMixin references AttachmentUtils (TB 10.1.x / 1.21.1+).
            // Skip silently on TB 10.0.x (1.21) where that class doesn't exist.
            try {
                Class.forName("com.tiviacz.travelersbackpack.capability.AttachmentUtils");
            } catch (ClassNotFoundException e) {
                return false;
            }
            return true;
        }
        return true;
    }

    // Required Boilerplate
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
