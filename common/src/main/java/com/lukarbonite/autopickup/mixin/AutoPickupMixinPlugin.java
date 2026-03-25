package com.lukarbonite.autopickup.mixin;

import com.lukarbonite.autopickup.platform.PlatformHelper;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class AutoPickupMixinPlugin implements IMixinConfigPlugin {

    private boolean hasVeinMiner;
    private boolean hasTreeHarvester;

    @Override
    public void onLoad(String mixinPackage) {
        PlatformHelper platform = PlatformHelper.get();
        hasVeinMiner = platform.isModLoaded("veinminer");
        hasTreeHarvester = platform.isModLoaded("treeharvester");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.veinminer")) {
            return hasVeinMiner;
        }
        if (mixinClassName.contains(".compat.treeharvester")) {
            return hasTreeHarvester;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}