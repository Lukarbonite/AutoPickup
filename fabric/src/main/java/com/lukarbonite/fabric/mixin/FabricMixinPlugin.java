package com.lukarbonite.fabric.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class FabricMixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.veinminer")) {
            return FabricLoader.getInstance().isModLoaded("veinminer");
        }
        if (mixinClassName.contains(".compat.treeharvester")) {
            return FabricLoader.getInstance().isModLoaded("treeharvester");
        }
        if (mixinClassName.contains(".compat.travelersbackpack")) {
            return FabricLoader.getInstance().isModLoaded("travelersbackpack");
        }
        return true;
    }

    // Standard boilerplate for unused methods
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}